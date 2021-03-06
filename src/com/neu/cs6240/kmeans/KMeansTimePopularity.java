package com.neu.cs6240.kmeans;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import au.com.bytecode.opencsv.CSVParser;

public class KMeansTimePopularity {

	public static class Config1Mapper extends
			Mapper<Object, Text, KMeansTimePopularityKey, Text> {

		// initialize CSVParser as comma separated values
		private CSVParser csvParser = new CSVParser(',', '"');
		private ArrayList<Text> centers = new ArrayList<Text>();

		@Override
		protected void setup(Context context) throws IOException,
				InterruptedException {
			super.setup(context);
			Configuration conf = context.getConfiguration();
			Path center = new Path(conf.get("center.config.1"));
			
			System.out.println("Map1 - Reading from : " + center.toString());
			
			FileSystem fs = center.getFileSystem(conf);

			BufferedReader br = new BufferedReader(new InputStreamReader(
					fs.open(center)));
			String line;
			while ((line = br.readLine()) != null) {
				centers.add(new Text(line));
			}
			br.close();
		}

		protected void map(Object offset, Text line, Context context)
				throws IOException, InterruptedException {

			// Parse the input line
			String[] parsedData = null;
			try {
				parsedData = this.csvParser.parseLine(line.toString());
			} catch (Exception e) {
				// In case of bad data record ignore them
				return;
			}

			if (parsedData.length != 3) {
				return;
			}

			Point p1 = new Point(parsedData[1], parsedData[2]);
			p1.setHashTag(parsedData[0]);

			double min1 = Double.MAX_VALUE;
			double min2 = Double.MAX_VALUE;
			Point nearestCenter = new Point(centers.get(0));

			// Find the minimum center from a point
			for (Text c : centers) {
				Point cp = new Point(c);
				min1 = cp.euclidian(p1);
				min2 = cp.euclidian(nearestCenter);
				if (Math.abs(min1) < Math.abs(min2)) {
					nearestCenter = cp;
					min2 = min1;
				}
			}

			// We found the nearest cluster
			KMeansTimePopularityKey key = new KMeansTimePopularityKey();
			key.setCenter(new Text(nearestCenter.toString()));
			key.setMapNumber(new IntWritable(1));

			context.write(key, line);
		}
	}

	public static class Config2Mapper extends
			Mapper<Object, Text, KMeansTimePopularityKey, Text> {

		// initialize CSVParser as comma separated values
		private CSVParser csvParser = new CSVParser(',', '"');
		private ArrayList<Text> centers = new ArrayList<Text>();

		@Override
		protected void setup(Context context) throws IOException,
				InterruptedException {
			super.setup(context);
			Configuration conf = context.getConfiguration();
			Path center = new Path(conf.get("center.config.2"));
			
			System.out.println("Map2 - Reading from : " + center.toString());
			
			FileSystem fs = center.getFileSystem(conf);

			BufferedReader br = new BufferedReader(new InputStreamReader(
					fs.open(center)));
			String line;
			while ((line = br.readLine()) != null) {
				centers.add(new Text(line));
			}
			br.close();
		}

		protected void map(Object offset, Text line, Context context)
				throws IOException, InterruptedException {

			// Parse the input line
			String[] parsedData = null;
			try {
				parsedData = this.csvParser.parseLine(line.toString());
			} catch (Exception e) {
				// In case of bad data record ignore them
				return;
			}

			if (parsedData.length != 3) {
				return;
			}

			Point p1 = new Point(parsedData[1], parsedData[2]);
			p1.setHashTag(parsedData[0]);

			double min1 = Double.MAX_VALUE;
			double min2 = Double.MAX_VALUE;
			Point nearestCenter = new Point(centers.get(0));

			// Find the minimum center from a point
			for (Text c : centers) {
				Point cp = new Point(c);
				min1 = cp.euclidian(p1);
				min2 = cp.euclidian(nearestCenter);
				if (Math.abs(min1) < Math.abs(min2)) {
					nearestCenter = cp;
					min2 = min1;
				}
			}

			// We found the nearest cluster
			KMeansTimePopularityKey key = new KMeansTimePopularityKey();
			key.setCenter(new Text(nearestCenter.toString()));
			key.setMapNumber(new IntWritable(2));

			context.write(key, line);
		}
	}

	public static class KMeansReducer extends
			Reducer<KMeansTimePopularityKey, Text, Text, NullWritable> {

		Vector<Text> centers = new Vector<Text>();
		private MultipleOutputs multiOutput;
		int MAPNO;
		private static String ITR_NO;
		private static String CENTER_BASE_PATH;

		public static enum HadoopCounter {
			ISCONVERED_CONFIG1,
			ISCONVERED_CONFIG2,
			ISCONVERED_CONFIG3
		}

		@Override
		public void setup(Context context) throws IOException, InterruptedException {
			super.setup(context);
			multiOutput = new MultipleOutputs(context);
			Configuration conf = context.getConfiguration();
			ITR_NO = conf.get("KMeans.iteration");
			CENTER_BASE_PATH = conf.get("center.base.path");
		}

		@Override
		public void reduce(KMeansTimePopularityKey key, Iterable<Text> values,
				Context context) throws IOException, InterruptedException {

			int mapperNumber = key.getMapNumber().get();
			MAPNO = mapperNumber;
			double newAvgTime = 0.0;
			double newPopularity = 0.0;
			int total = 0;
			List<Text> data = new ArrayList<Text>();

			for (Text value : values) {
				data.add(new Text(value));
				String[] parsed = value.toString().split(",");
				newAvgTime += Double.parseDouble(parsed[1]);
				newPopularity += Double.parseDouble(parsed[2]);
				total++;
			}

			// New centers
			newAvgTime /= total;
			newPopularity /= total;

			String newCenter = String.valueOf(newAvgTime) + ","
					+ String.valueOf(newPopularity);
			Text newC = new Text(newCenter);
			centers.add(newC);

			System.out.println("New Centroid : " + newC.toString());
			Text newPoint = new Text(String.valueOf(newAvgTime) + ","
					+ String.valueOf(newPopularity));

			writeResults(mapperNumber, newPoint, false);

			Point pnt1 = new Point(newC);
			Point pnt2 = new Point(key.getCenter());

			double diff = pnt1.euclidian(pnt2);
			System.out.println("Diff = " + diff);
			if (Math.abs(diff) < 1) {
				writeResults(mapperNumber, key.getCenter(), true);
//				for (Text d : data) {
//					writeResults(mapperNumber, new Text(d), true);
//				}
			} else {
				switch(mapperNumber){
					case 1:
						context.getCounter(HadoopCounter.ISCONVERED_CONFIG1).increment(1);
						break;
					case 2:
						context.getCounter(HadoopCounter.ISCONVERED_CONFIG2).increment(1);
						break;
					case 3:
						context.getCounter(HadoopCounter.ISCONVERED_CONFIG3).increment(1);
						break;
				}				
			}
		}

		private void writeResults(int mapperNumber, Text point,
				boolean isConverged) throws IOException, InterruptedException {
			String name1 = "";
			String path = "";
			String name2 = "";			
			String name3 = "";
			
			

			if (isConverged) {
				name1 = "firstConverged";
				name2 = "secondConverged";
				name3 = "thirdConverged";
			}else{
				name1 = "first";
				name2 = "second";
				name3 = "third";
			}

			path = CENTER_BASE_PATH + "/conf" + mapperNumber + "/" + ITR_NO + "/";
			
			switch (mapperNumber) {
				case 1:
					multiOutput.write(name1, point, NullWritable.get(),path);
					break;
				case 2:
					multiOutput.write(name2, point, NullWritable.get(),path);
					break;
				case 3:
					multiOutput.write(name3, point, NullWritable.get(),path);
					break;
				default:
					break;
			}
		}

		@Override
		public void cleanup(Context context) throws IOException,
				InterruptedException {
			super.cleanup(context);
			multiOutput.close();
			Configuration conf = context.getConfiguration();
			Path center = null;
			FileSystem fs;
			BufferedWriter bw;

			switch (MAPNO) {
				case 1:
					center = new Path(conf.get("center.config.1"));
					break;
				case 2:
					center = new Path(conf.get("center.config.2"));
					break;
				case 3:
					break;
				default:
					break;
			}

			if (null != center) {
				fs = center.getFileSystem(conf);
				fs.delete(center, true);

				bw = new BufferedWriter(
						new OutputStreamWriter(FileSystem.create(fs, center,
								FsPermission.getDefault())));

				for (Text singleCenter : centers) {
					bw.write(singleCenter.toString() + "\n");
				}

				bw.close();
			}
		}
	}

	public static class KMeansPartitioner extends
			Partitioner<KMeansTimePopularityKey, Text> {
		@Override
		public int getPartition(KMeansTimePopularityKey key, Text value,
				int numPartitions) {
			KMeansTimePopularityKey k = (KMeansTimePopularityKey) key;
			return Math.abs(k.getMapNumber().get()) % numPartitions;
		}
	}

	/**
	 * @param args
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws IOException,
			InterruptedException, ClassNotFoundException {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args)
				.getRemainingArgs();
		if (otherArgs.length != 3) {
			System.err.println("Usage: KMeansTimePopularity <in> <center>");
			System.exit(2);
		}

		int iteration = 1;
		long counter1 = 1;
		long counter2 = 1;
		long counter3 = 1;

		do {
			conf = new Configuration();
			Path input_1 = new Path(otherArgs[0]);
			Path input_2 = new Path(otherArgs[2]);
			// Path input_2 = new Path(otherArgs[0] + String.valueOf(iteration -
			// 1) + "/s*");
			// Path input_3 = new Path(otherArgs[0] + String.valueOf(iteration -
			// 1) + "/t*");

			Path center_1 = new Path(otherArgs[1] + "/conf1/"  
					+ String.valueOf(iteration - 1) + "/-r-00000");
			Path center_2 = new Path(otherArgs[1] + "/conf2/"
					+ String.valueOf(iteration - 1) + "/-r-00000");
			// Path center_3 = new Path(otherArgs[1] + "center6.txt");
			
//			Path center_1 = new Path(otherArgs[1] + "00/first-r-00000");
//			Path center_2 = new Path(otherArgs[1] + "01/second-r-00000");

			Path output = new Path(otherArgs[1] + String.valueOf(iteration));
//			Path output = new Path(otherArgs[1]);

			conf.set("center.config.1", center_1.toString());
			conf.set("center.config.2", center_2.toString());
			conf.set("KMeans.iteration", String.valueOf(iteration));
			conf.set("center.base.path", otherArgs[1]);
			
			// conf.set("center.path.3", center_3.toString());

			Job job = new Job(conf, "KMeansTimePopularity");
			job.setJobName("K Means Clustering for Avg Time and popularity");

			FileSystem fs;
			fs = output.getFileSystem(conf);
			fs.delete(output, true);

			job.setJarByClass(KMeansTimePopularity.class);
			job.setMapperClass(Config1Mapper.class);
			job.setMapperClass(Config2Mapper.class);
			// job.setMapperClass(KMeansMapper3.class);
			job.setPartitionerClass(KMeansPartitioner.class);
			job.setReducerClass(KMeansReducer.class);

			job.setMapOutputKeyClass(KMeansTimePopularityKey.class);
			job.setMapOutputValueClass(Text.class);

			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);

			MultipleInputs.addInputPath(job, input_1, TextInputFormat.class,
					Config1Mapper.class);
			MultipleInputs.addInputPath(job, input_2, TextInputFormat.class,
					Config2Mapper.class);
			
			// MultipleInputs.addInputPath(job, input_3, TextInputFormat.class,
			// KMeansMapper3.class);

			FileOutputFormat.setOutputPath(job, output);

			MultipleOutputs.addNamedOutput(job, "first",
					TextOutputFormat.class, Text.class, NullWritable.class);
			MultipleOutputs.addNamedOutput(job, "firstConverged",
					TextOutputFormat.class, Text.class, NullWritable.class);
			MultipleOutputs.addNamedOutput(job, "second",
					TextOutputFormat.class, Text.class, NullWritable.class);
			MultipleOutputs.addNamedOutput(job, "secondConverged",
					TextOutputFormat.class, Text.class, NullWritable.class);

			// MultipleOutputs.addNamedOutput(job, "third",
			// TextOutputFormat.class, Text.class, NullWritable.class);

			job.waitForCompletion(true);

			iteration++;
			counter1 = job.getCounters()
					.findCounter(KMeansReducer.HadoopCounter.ISCONVERED_CONFIG1)
					.getValue();
			counter2 = job.getCounters()
					.findCounter(KMeansReducer.HadoopCounter.ISCONVERED_CONFIG2)
					.getValue();
			counter3 = job.getCounters()
					.findCounter(KMeansReducer.HadoopCounter.ISCONVERED_CONFIG3)
					.getValue();

		} while (counter1 > 0 || counter2 > 0 || counter3 > 0);

	}

}
