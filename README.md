If you came here from the CHUG, you might want to see the slide deck [here](https://github.com/cestella/SenatorialSpeechInvestigation/raw/master/political-nlp-analysis/src/main/latex/CHUG_presentation.pdf)
#Introduction
This is an illustrative project intended to demonstrate a simple, yet not quite as simple as Word Count, NLP algorithm implemented in Hadoop Map Reduce.  This is also intended to demonstrate my opinions on unit testing Map Reduce jobs.

#Description
The purpose of the project is to take a dataset of around 1400 speeches from Senators along with an ideal-point mapping onto the number line of the politicians based around the work of [Simon Jackman](http://jackman.stanford.edu/blog/?p=2084) to do the following:

* Find partition points separating Conservative, Moderate and Liberal senators by dividing the probability density into thirds (see histogram)
* Use this partition to construct three corpuses associated with Conservative, Moderate and Liberal senators
* Preprocess using a Porter Stemmer and rank the tokenized terms by [Inverse Document Frequency](http://en.wikipedia.org/wiki/Okapi_BM25#IDF_Information_Theoretic_Interpretation)
* Take the ranked terms and output the terms that are important in the corpus associated with one political orientation but not the others.

##Partition
The partition of the probability space is split into even thirds as can be seen here:

![density plot](https://github.com/cestella/SenatorialSpeechInvestigation/raw/master/political-nlp-analysis/src/main/R/histogram.png "Density Plot")


##Output
The results, for the curious, are here:
<table>
<tr><td>LIBERAL</td><td>MODERATE</td><td>CONSERVATIVE</td></tr>
<tr><td>market</td><td>produce</td><td>discuss</td></tr>
<tr><td>power</td><td>commission</td><td>past</td></tr>
<tr><td>institutions</td><td>strengthening</td><td>politics</td></tr>
<tr><td>initiate</td><td>provisions</td><td>pointed</td></tr>
<tr><td>implement</td><td>subcommittee</td><td>direct</td></tr>
<tr><td>establish</td><td>impact</td><td>different</td></tr>
<tr><td>history</td><td>north</td><td>account</td></tr>
<tr><td>available</td><td>managers</td><td>failed</td></tr>
<tr><td>school</td><td>sure</td><td>debate</td></tr>
<tr><td>according</td><td>hard</td><td>instead</td></tr>
<tr><td>risk</td><td>sent</td><td>rates</td></tr>
<tr><td>measures</td><td>defense</td><td>reason</td></tr>
<tr><td>children</td><td>fiscal</td><td>congressional</td></tr>
<tr><td>expand</td><td>honorable</td><td>budgeting</td></tr>
<tr><td>trained</td><td>ability</td><td>question</td></tr>
</table>

#Current Caveats
* A naive partition based on an even partition of the probability space may be unsuitable.  I didn't know of a better, politically agnostic way to do this.
* An analysis based on n-grams would be more appropriate or at least a more intelligent chunking algorithm so that words like "north korea" are not split.
* This is not as efficient as it could be.  I could have used term IDs instead of the actual terms, but I wanted the Map Reduce job to be as clear as possible

This code is mostly an intellectual lark and a demonstration of NLP done using Map Reduce that does not require the baggage of Mahout.

#Usage
##Prerequisites
To execute this, you must have:

* A JDK installed
* Maven 2 or higher installed

To generate the histogram, you need to have R.
To generate the presentation from the Cleveland CHUG, you need latex

##To generate the full example
From the command line, in the political-nlp-analysis directory, execute

     mvn integration-test

You can then use the political-nlp-analysis/generate_top_words.sh script
to generate the top words for a particular political orientation thusly:

     generate_top_words.sh {LIBERAL, MODERATE, CONSERVATIVE} <number of terms>

You can reconstruct the density plot using the political-nlp-analysis/src/main/R/generate_histogram.sh script:

     generate_histogram.sh <path to ideal_points.csv>

#Contact

If anyone has comments, concerns or criticisms, please let me hear about it at cestella@gmail.com

