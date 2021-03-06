package tv.floe.metronome.deeplearning.neuralnetwork.optimize;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.mahout.math.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cc.mallet.optimize.Optimizable;

import tv.floe.metronome.deeplearning.neuralnetwork.core.BaseNeuralNetworkVectorized;
import tv.floe.metronome.deeplearning.neuralnetwork.optimize.util.CustomConjugateGradient;
import tv.floe.metronome.math.MatrixUtils;

/**
 * Used primiarily as the base class for RestrictedBoltzmannMachineOptimizer
 * 
 * @author josh
 *
 */
public abstract class NeuralNetworkOptimizer implements Optimizable.ByGradientValue,Serializable {

	public NeuralNetworkOptimizer(BaseNeuralNetworkVectorized network, double lr,Object[] trainingParams) {
		
		this.network = network;
		this.lr = lr;
		this.extraParams = trainingParams;
		
	}


	private static final long serialVersionUID = 4455143696487934647L;
	protected BaseNeuralNetworkVectorized network;
	protected double lr;
	protected Object[] extraParams;
	protected double tolerance = 0.0001;
	protected static Logger log = LoggerFactory.getLogger(NeuralNetworkOptimizer.class);
	protected List<Double> errors = new ArrayList<Double>();
	protected double minLearningRate = 0.001;
	protected transient CustomConjugateGradient opt;
	
	
	
	public void train(Matrix x) {
		
		if (opt == null) {
			//opt = new cc.mallet.optimize.LimitedMemoryBFGS(this);
			opt = new CustomConjugateGradient(this);
		}
		
		opt.setTolerance(tolerance);
		int epochs = 5000; // default
		if ( extraParams.length > 2) {
			epochs = (Integer) extraParams[2];
		}
		
		opt.optimize( epochs );
		

	}
	
	// used in NeuralNetworkEpochListener
	/*
	@Override
	public void epochDone(int epoch) {
		int plotEpochs = network.getRenderEpochs();
		if(epoch % plotEpochs == 0 || epoch == 0) {
			NeuralNetPlotter plotter = new NeuralNetPlotter();
			plotter.plotNetworkGradient(network,network.getGradient(extraParams));
		}
	}
	*/


	public List<Double> getErrors() {
		return errors;
	}


	/**
	 * 
	 * Returns (visible_neurons x hidden_neurons) + hidden_bias_neurons + vis_bias_neurons 
	 * 
	 */
	@Override
	public int getNumParameters() {
		return MatrixUtils.length(network.connectionWeights ) + MatrixUtils.length( network.hiddenBiasNeurons ) + MatrixUtils.length( network.visibleBiasNeurons );
	}


	@Override
	public void getParameters(double[] buffer) {
		/*
		 * If we think of the parameters of the model (W,vB,hB)
		 * as a solid line for the optimizer, we get the following:
		 * 
		 */

		
		for (int i = 0; i < buffer.length; i++) {
			buffer[ i ] = getParameter( i );
		}

		
	}


	@Override
	public double getParameter(int index) {
		
		// beyond weight matrix
		if (index >= MatrixUtils.length( network.connectionWeights ) ) {
			
			int i = getAdjustedIndex(index);

			//beyond visible bias
			if (index >= MatrixUtils.length( network.visibleBiasNeurons ) + MatrixUtils.length( network.connectionWeights ) ) {
				
				//return network.hBias.get(i);
				return MatrixUtils.getElement( network.hiddenBiasNeurons, i );
				
			} else {
				
				//return network.vBias.get(i);
				return MatrixUtils.getElement( network.visibleBiasNeurons, i );
				
			}
			
			
		}
		
		return MatrixUtils.getElement( network.connectionWeights, index );

	}
	



	/**
	 * 
	 * If we think of the parameters of the model (W,vB,hB)
	 * as a solid line for the optimizer, we get the following:
	 * 
	 */
	@Override
	public void setParameters(double[] params) {
		
		for (int i = 0; i < params.length; i++) {
		
			setParameter( i, params[ i ] );
			
		}

		
	}


	@Override
	public void setParameter(int index, double value) {

		
		// beyond weight matrix
		if (index >= MatrixUtils.length( network.connectionWeights ) ) {
			
			
			// beyond visible bias
			if (index >= MatrixUtils.length( network.visibleBiasNeurons ) + MatrixUtils.length( network.connectionWeights ) ) {
				
				int i = getAdjustedIndex(index);
				MatrixUtils.setElement( network.hiddenBiasNeurons, i, value );
				
			} else {
				
				int i = getAdjustedIndex(index);
				MatrixUtils.setElement( network.visibleBiasNeurons, i, value );
				
			}
			
			
		} else {
		
			MatrixUtils.setElement( network.connectionWeights, index, value );
			
		}
		
		
	}
	
	private int getAdjustedIndex(int index) {
		//int wLength = network.W.length;
		int wLength = MatrixUtils.length( network.connectionWeights );
		
		//int vBiasLength = network.vBias.length;
		int vBiasLength = MatrixUtils.length( network.visibleBiasNeurons );
		
		if(index < wLength)
			return index;
		else if(index >= wLength + vBiasLength) {
			int hIndex = index - wLength - vBiasLength;
			return hIndex;
		}
		else {
			int vIndex = index - wLength;
			return vIndex;
		}
	}	


	@Override
	public abstract void getValueGradient(double[] buffer);


	@Override
	public double getValue() {
		return -network.getReConstructionCrossEntropy();
	}



}
