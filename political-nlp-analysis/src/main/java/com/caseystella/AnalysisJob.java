package com.caseystella;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;

import com.caseystella.mr.MapKey;
import com.caseystella.util.NLPUtil;
import com.caseystella.util.NLPUtil.ImmutableToken;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

public class AnalysisJob 
{
	public static String PARAM_TOTAL_NUM_DOCS = "totalNumDocs";
	public static String PARAM_STOPWORDS = "stopwords";
	
	public static class Mapper extends MapReduceBase 
							  implements org.apache.hadoop.mapred.Mapper<LongWritable, Text, MapKey, Text>
	{
		protected Set<String> stopwords;
		
		protected Set<String> getStopwords() {
			return stopwords;
		}
		
		@Override
		public void configure(JobConf job) 
		{
			stopwords = new HashSet<String>(job.getStringCollection(PARAM_STOPWORDS));
			
		}
		
		
		@Override
		public void map( LongWritable arg0
					   , Text document
					   , OutputCollector<MapKey, Text> collector
					   , Reporter reporter
					   )
		throws IOException 
		{
			StringBuffer documentIdStr = new StringBuffer();
			int documentOffset = 0;
			int documentId = -1;
			String documentStr = null;
			for(;document.charAt(documentOffset) != '\t';++documentOffset)
			{
				documentIdStr.append((char)document.charAt(documentOffset));
			}
			
			documentId = Integer.parseInt(documentIdStr.toString());
			documentStr = document.toString().substring(documentOffset).trim();
			for(ImmutableToken token : NLPUtil.INSTANCE.tokenizeDocument(documentStr, getStopwords()))
			{
				MapKey key = new MapKey(token.getStemmedToken(), documentId);
				collector.collect(key, new Text(token.getToken()));
			}
			
		}
		
		
	}
	
	public static class Combiner extends MapReduceBase
								 implements org.apache.hadoop.mapred.Reducer<MapKey, Text, MapKey, Text>
	{

		@Override
		public void reduce(MapKey key, Iterator<Text> values,
				OutputCollector<MapKey, Text> collector, Reporter reporter)
				throws IOException 
		{
			Set<Text> duplicateSet = new HashSet<Text>();
			while(values.hasNext())
			{
				duplicateSet.add(values.next());
			}
			for(Text rawTokens : duplicateSet)
			{
				collector.collect(key, rawTokens);
			}
		}
		
	}
	
	public static class Partitioner extends MapReduceBase implements org.apache.hadoop.mapred.Partitioner<MapKey, Text>
	{

		@Override
		public int getPartition(MapKey key, Text value, int numReducers) 
		{
			return key.getTerm().hashCode() % numReducers;
		}
		
	}
	
	public static class Reducer extends MapReduceBase
								implements org.apache.hadoop.mapred.Reducer<MapKey, Text,  DoubleWritable, Text>
	{
		private static class State
		{
			
			String currentTerm;
			Set<Text> rawValues;
			int numDocsForTerm;
			
			public State(String currentTerm)
			{
				this.currentTerm = currentTerm;
				rawValues = new HashSet<Text>();
				numDocsForTerm = 1;
			}
			
			
			
		}
		
		State currentState = null;
		
		
		protected int totalNumDocs = 0;
		
		@Override
		public void configure(JobConf job) {
			totalNumDocs = job.getInt(PARAM_TOTAL_NUM_DOCS, -1);
		}
		
		protected int getTotalNumDocs() {
			return totalNumDocs;
		}
		
		@Override
		public void reduce( MapKey key
						  , Iterator<Text> values
						  , OutputCollector<DoubleWritable, Text> collector
						  , Reporter reporter
						  )
				throws IOException 
		{
			if(currentState != null && key.getTerm().equals(currentState.currentTerm))
			{
				currentState.numDocsForTerm++;
				while(values.hasNext())
				{
					currentState.rawValues.add(values.next());
				}
			}
			else
			{
				if(currentState != null && currentState.currentTerm != null)
				{
					double probability = 1.0*currentState.numDocsForTerm/getTotalNumDocs();
					//format: probability \t stemmed term \t all matching words with image as stemmed term
					collector.collect(new DoubleWritable(probability)
									 , new Text(Joiner.on('\t')
											 		  .join( currentState.currentTerm
											 			   , Joiner.on(' ')
											 			   		   .join(currentState.rawValues)
											 			   )
											   )
									 );
				}
				currentState = new State(key.getTerm());
				
			}
		}
	}
	
	public static void printHelp(Options options)
	{
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp( "analysis_job", options );
	}
	
	public static void main(String... argv) throws IOException, ParseException
	{
		
		CommandLineParser parser = new PosixParser();
		// create Options object
		Options options = new Options();
		
		{
			options.addOption( OptionBuilder.withArgName("input_file")
											.hasArg()
											.withDescription("Data input file")
											.isRequired()
											.withLongOpt("input-file")
											.create("i")
							 );
			options.addOption( OptionBuilder.withArgName("output_file")
					.hasArg()
					.withDescription("Data output file")
					.isRequired()
					.withLongOpt("output-file")
					.create("o")
					);
			options.addOption( OptionBuilder.withArgName("total_documents")
					.hasArg()
					.withDescription("Total number of documents")
					.isRequired()
					.withLongOpt("total-documents")
					.create("n")
					);
			options.addOption( OptionBuilder.withArgName("stopword_file")
					.hasArg()
					.withDescription("stopword file")
					.isRequired()
					.withLongOpt("stopword-file")
					.create("s")
					);
			options.addOption( OptionBuilder
					
					.withDescription("show this screen")
					
					.withLongOpt("help")
					.create("h")
					);
		}
		
		// parse the command line arguments
		CommandLine line = null;
		try
		{
			line = parser.parse( options, argv );
		}
		catch(ParseException ex)
		{
			System.err.println("ERROR: " + ex.getMessage());
			printHelp(options);
			return;
		}
	    
		String inFile = line.getOptionValue('i');
		int totalDocuments = Integer.parseInt(line.getOptionValue('n'));
		String outFile = line.getOptionValue('o');
		String stopFile = line.getOptionValue('s');
		
		JobConf conf = new JobConf(AnalysisJob.class);
		conf.setJobName("analysis - " + inFile + " -> " + outFile);
		conf.setInt(PARAM_TOTAL_NUM_DOCS, totalDocuments);
		
		
		// the keys are words (strings)
	    conf.setOutputKeyClass(DoubleWritable.class);
	    // the values are counts (ints)
	    conf.setOutputValueClass(Text.class);
	    
	    conf.setMapperClass(Mapper.class);
	    conf.setPartitionerClass(Partitioner.class);
	    conf.setCombinerClass(Combiner.class);
	    conf.setReducerClass(Reducer.class);
	    
	    ArrayList<String> stopwords = Files.readLines( new File(stopFile)
	    										, Charsets.US_ASCII
	    										, new LineProcessor<ArrayList<String>>() 
	    										  {
	    											ArrayList<String> lines = null;
	    											@Override
	    											public ArrayList<String> getResult() {
	    												return lines;
	    											}
	    											@Override
	    											public boolean processLine(String line) throws IOException 
	    											{
	    												lines.add(line.toLowerCase());
	    												return true;
	    											}
												  }
	    										);
	    
	    conf.setStrings( PARAM_STOPWORDS
	    			   , stopwords.toArray(new String[stopwords.size()])
	    			   );
	    FileInputFormat.addInputPath(conf, new Path(inFile));
	    FileOutputFormat.setOutputPath(conf, new Path(outFile));

	    JobClient.runJob(conf);
	}
}
