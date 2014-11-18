/**
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.neu.cs6240.TopKExperts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import au.com.bytecode.opencsv.CSVParser;

public class JoinQA {

	public static class JoinQAMapper extends
			Mapper<Object, Text, JoinQAKey, JoinQAValue> {
		// initialize CSVParser as comma separated values
		private CSVParser csvParser = new CSVParser(',', '"');

		public void map(Object offset, Text line, Context context)
				throws IOException, InterruptedException {

			// Parse the input line
			String[] parsedData = this.csvParser.parseLine(line.toString());
			JoinQAValue value = null;
			JoinQAKey key = null;

			if ((!parsedData[1].isEmpty()) && parsedData[1].equals("1") && (! parsedData[2].equals("None"))) {
				// Question
				key = new JoinQAKey(parsedData[2], "Q");
				value = new JoinQAValue("", parsedData[13]);
			} else {
				// Answer
				key = new JoinQAKey(parsedData[0], "A");
				value = new JoinQAValue(parsedData[7], "");
			}

			context.write(key, value);
		}
	}

	public static class JoinQAReducer extends
			Reducer<JoinQAKey, JoinQAValue, Text, Text> {

		public void reduce(JoinQAKey key, Iterable<JoinQAValue> values,
				Context context) throws IOException, InterruptedException {

			ArrayList<JoinQAValue> questions = new ArrayList<JoinQAValue>();

			for (JoinQAValue value : values) {
				if (key.getFlag().equals("A")) {
					questions.add(value);
				} else {
					Iterator<JoinQAValue> questionIterator = questions.iterator();
					while (questionIterator.hasNext()) {
						JoinQAValue question = questionIterator.next();
						context.write(question.getHashTags(), value.getUserId());
					}
				}
			}

		}
	}

	public static class JoinQAPartitioner extends
			Partitioner<JoinQAKey, JoinQAValue> {
		/**
		 * Based on the configured number of reducer, this will partition the
		 * data approximately evenly based on number of unique carrier names
		 */
		@Override
		public int getPartition(JoinQAKey key, JoinQAValue value,
				int numPartitions) {
			// multiply by 127 to perform some mixing
			return Math.abs(key.getPostId().hashCode() * 127) % numPartitions;
		}
	}

	public static class JoinQAGroupComparator extends WritableComparator {
		protected JoinQAGroupComparator() {
			super(JoinQAKey.class, true);
		}

		@Override
		public int compare(WritableComparable w1, WritableComparable w2) {
			JoinQAKey key1 = (JoinQAKey) w1;
			JoinQAKey key2 = (JoinQAKey) w2;
			return key1.getPostId().compareTo(key2.getPostId());
		}
	}	
		
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args)
				.getRemainingArgs();
		if (otherArgs.length != 2) {
			System.err.println("Usage: JoinQA <in> <out>");
			System.exit(2);
		}
		Job job = new Job(conf, "JoinQA");
		job.setJarByClass(JoinQA.class);
		job.setMapperClass(JoinQAMapper.class);
		job.setReducerClass(JoinQAReducer.class);
		job.setOutputKeyClass(JoinQAKey.class);
		job.setOutputValueClass(JoinQAValue.class);
		job.setPartitionerClass(JoinQAPartitioner.class);
	    job.setGroupingComparatorClass(JoinQAGroupComparator.class);
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}