package tv.floe.metronome.deeplearning.dbn.iterativereduce;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.util.ToolRunner;

import tv.floe.metronome.deeplearning.datasets.DataSet;
import tv.floe.metronome.deeplearning.datasets.iterator.impl.MnistHDFSDataSetIterator;
import tv.floe.metronome.deeplearning.dbn.DeepBeliefNetwork;
import tv.floe.metronome.io.records.CachedVectorReader;

import com.cloudera.iterativereduce.ComputableWorker;
import com.cloudera.iterativereduce.io.RecordParser;
import com.cloudera.iterativereduce.io.TextRecordParser;
import com.cloudera.iterativereduce.yarn.appworker.ApplicationWorker;

/**
 * Base IterativeReduce worker node
 * 
 * TODO
 * - setup a base DBN instance to run locally
 * 
 * @author josh
 *
 */
public class WorkerNode implements ComputableWorker<DBNParameterVectorUpdateable> {
	
	private static final Log LOG = LogFactory.getLog(WorkerNode.class);
	
	protected Configuration conf = null;	  

	DeepBeliefNetwork dbn = null;
	
	// TODO: need to rethink these because
	TextRecordParser lineParser = new TextRecordParser();
	CachedVectorReader cachedVecReader = null; //new CachedVectorReader(lineParser, rec_factory); 

	private boolean IterationComplete = false;
	private int CurrentIteration = 0;
	

	// TODO: setup vars from conf
	int[] hiddenLayerSizes = { 500, 250, 100 };
	double learningRate = 0.01;
	int preTrainEpochs = 100;
	int fineTuneEpochs = 100;
	int totalNumExamples = 20;	
	
	int batchSize = 1;
	boolean showNetworkStats = true;
	
	int numIns = 784; 
	int numLabels = 10; 

	int n_layers = hiddenLayerSizes.length;
	
	
	RandomGenerator rng = new MersenneTwister(123);
	
	MnistHDFSDataSetIterator hdfs_fetcher = null; //new MnistHDFSDataSetIterator( batchSize, totalNumExamples, txt_reader );
	
	/**
	 * generates the local DBN parameter vector
	 *  
	 * 
	 */
	public DBNParameterVector GenerateParameterVectorUpdate() {

		DBNParameterVector vector = new DBNParameterVector();
		//vector.parameter_vector = this.polr.getBeta().clone(); // this.polr.getGamma().getMatrix().clone();

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		this.dbn.write(out);
		
		vector.dbn_payload = out.toByteArray();
		
		/*
		if (this.lineParser.hasMoreRecords()) {
			vector.IterationComplete = 0;
		} else {
			vector.IterationComplete = 1;
		}

		vector.CurrentIteration = this.CurrentIteration;
*/

/*
		vector.batchTimeMS = this.lastBatchTimeMS;

		vector.AvgError = (new Double(metrics.AvgError * 100))
				.floatValue();
		vector.TrainedRecords = (new Long(metrics.TotalRecordsProcessed))
				.intValue();
		
*/
		return vector;

	}	
	
	
	@Override
	public boolean IncrementIteration() {
		this.CurrentIteration++;

		return false;
	}
	
	/**
	 * Run a training pass of a single batch of input records on the DBN
	 * 
	 */
	@Override
	public DBNParameterVectorUpdateable compute() {
		
		// TODO: setup a cached vector system from hdfs for batches
						
		
		// mini-batches through dataset
//		MnistDataSetIterator fetcher = new MnistDataSetIterator( batchSize, totalNumExamples );
		DataSet hdfs_recordBatch = this.hdfs_fetcher.next();
				
		int recordsProcessed = 0;
		
		StopWatch watch = new StopWatch();
		watch.start();
		
		StopWatch batchWatch = new StopWatch();
		
		if (hdfs_recordBatch.getFirst().numRows() > 0) {
//		do  {
		
			// calc stats on number records processed
			recordsProcessed += hdfs_recordBatch.getFirst().numRows();
			
			System.out.println( "PreTrain: Batch Mode, Processed Total " + recordsProcessed + ", Elapsed Time " + watch.toString() );
			
			batchWatch.reset();
			batchWatch.start();
//			dbn.preTrain( recordBatch.getFirst(), 1, learningRate, preTrainEpochs);
			this.dbn.preTrain( hdfs_recordBatch.getFirst(), 1, this.learningRate, this.preTrainEpochs);
			batchWatch.stop();
			
			System.out.println( "Batch Training Elapsed Time " + batchWatch.toString() );

			//System.out.println( "DBN Network Stats:\n" + dbn.generateNetworkSizeReport() );

/*			
			if (fetcher.hasNext()) {
				first = fetcher.next();
			}
			
		} while (fetcher.hasNext());
*/
		} // if

		// this is a clunky way to do this. dont judge me, working fast here.
		DBNParameterVector dbn_update = new DBNParameterVector();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		this.dbn.write(out);
		dbn_update.dbn_payload = out.toByteArray();
		
		DBNParameterVectorUpdateable updateable = new DBNParameterVectorUpdateable();
		updateable.param_msg = dbn_update;
		
		return updateable;
	}
	
	@Override
	public DBNParameterVectorUpdateable compute(List<DBNParameterVectorUpdateable> arg0) {
		
		return compute();
		
	}
	
	@Override
	public DBNParameterVectorUpdateable getResults() {
		
		return new DBNParameterVectorUpdateable( this.GenerateParameterVectorUpdate() );
	
	}
	
	/**
	 * TODO: re-wire this to read blocks of records into a Matrix
	 * 
	 */
	@Override
	public void setRecordParser(RecordParser lineParser) {

//		this.lineParser = (TextRecordParser) rp;
//		this.cachedVecReader = new CachedVectorReader(lineParser, rec_factory);
		
		try {
			this.hdfs_fetcher = new MnistHDFSDataSetIterator( batchSize, totalNumExamples, (TextRecordParser)lineParser );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Setup the local DBN instance based on conf params
	 * 
	 */
	@Override
	public void setup(Configuration c) {
		
		



	    this.conf = c;
	    
	    String useRegularization = "false";
	    
	    try {
	      
	      
//	    this.NumberIterations = this.conf.getInt("app.iteration.count", 1);
	      
	      this.learningRate = Double.parseDouble(this.conf.get(
		          "tv.floe.metronome.dbn.conf.LearningRate", "0.001"));
	      
	      this.batchSize = this.conf.getInt("tv.floe.metronome.dbn.conf.batchSize",  1);
	      
	      this.numIns = this.conf.getInt( "tv.floe.metronome.dbn.conf.numberInputs", 784);
	      
	      this.numLabels = this.conf.getInt( "tv.floe.metronome.dbn.conf.numberLabels", 10 );
	      
	      //500, 250, 100
	      String hiddenLayerConfSizes = this.conf.get( "tv.floe.metronome.dbn.conf.hiddenLayerSizes" );
	      
	      String[] layerSizes = hiddenLayerConfSizes.split(",");
	      this.hiddenLayerSizes = new int[ layerSizes.length ];
	      for ( int x = 0; x < layerSizes.length; x++ ) {
	    	  this.hiddenLayerSizes[ x ] = Integer.parseInt( layerSizes[ x ] );
	      }
	      

	
		    useRegularization = this.conf.get("tv.floe.metronome.dbn.conf.useRegularization");
	
		
			
			this.n_layers = hiddenLayerSizes.length;
			
			
			this.dbn = new DeepBeliefNetwork( numIns, hiddenLayerSizes, numLabels, n_layers, rng ); //, Matrix input, Matrix labels);
	
			if (useRegularization != null && useRegularization.equals("true")) {
		    	this.dbn.useRegularization = true;
		    }
			
			this.dbn.setSparsity( Double.parseDouble( this.conf.get( "tv.floe.metronome.dbn.conf.sparsity", "0.01") ) );
			this.dbn.setMomentum( Double.parseDouble( this.conf.get( "tv.floe.metronome.dbn.conf.momentum", "0" ) ) );		
			
	      
	      
	    } catch (Exception e) {
	      // TODO Auto-generated catch block
	      e.printStackTrace();
	    }
	    		

	
		
	}
	
	/**
	 * Collect the update from the master node and apply it to the local 
	 * parameter vector
	 * 
	 */
	@Override
	public void update(DBNParameterVectorUpdateable master_update_updateable) {

		DBNParameterVector master_update = master_update_updateable.get();
		
		ByteArrayInputStream b = new ByteArrayInputStream( master_update.dbn_payload );
		
		
		// now update the local DBN worker instance
		this.dbn.load(b);
		
		
	}
	
	

	protected String LoadStringConfVarOrException(String ConfVarName, String ExcepMsg) throws Exception {
		    
		if (null == this.conf.get(ConfVarName)) {
			
			throw new Exception(ExcepMsg);
			
		} else {
			
			return this.conf.get(ConfVarName);
			
		}
		    
	}
  
	protected int LoadIntConfVarOrException(String ConfVarName, String ExcepMsg) throws Exception {
	    
		if (null == this.conf.get(ConfVarName)) {

			throw new Exception(ExcepMsg);
			
		} else {
			
			return this.conf.getInt(ConfVarName, 0);
			
		}
	    
	}	

	public static void main(String[] args) throws Exception {

		TextRecordParser parser = new TextRecordParser();
		WorkerNode wn = new WorkerNode();
		ApplicationWorker<DBNParameterVectorUpdateable> aw = new ApplicationWorker<DBNParameterVectorUpdateable>(parser, wn, DBNParameterVectorUpdateable.class);
			    
		ToolRunner.run(aw, args);
		
	}
	


}
