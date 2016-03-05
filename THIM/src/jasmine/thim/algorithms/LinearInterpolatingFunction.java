package jasmine.thim.algorithms;

import microsim.data.MultiKeyCoefficientMap;

import java.util.TreeMap;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.math.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;

public class LinearInterpolatingFunction {

	/**
	 * The values of the domain must be listed as the first key in the MultiKeyCoefficientMap.  
	 * @param inputMap - The inputMap does not have to specify the order of the keys, i.e. the keys do not have to be in ascending order
	 * @return A piecewise linear function, whose value for all valid points in the domain can be 
	 * obtaining using the .value() method. 
	 * 
	 * @author Ross Richardson
	 */
	public static PolynomialSplineFunction create(MultiKeyCoefficientMap inputMap) {

		TreeMap<Double, Double> sortedInputMap = new TreeMap<Double, Double>();
	
		for (Object multiKey : inputMap.keySet()) {
			final Double key = ((Number)((MultiKey) multiKey).getKey(0)).doubleValue();			//The values of the domain must be listed as the first key in the MultiKey
			sortedInputMap.put(key.doubleValue(), ((Number)inputMap.get(multiKey)).doubleValue());
		}
		
		int i=0;
		double[] xPoints = new double[inputMap.keySet().size()]; 
		double[] yPoints = new double[xPoints.length];

		for (Double key : sortedInputMap.keySet()) {
			xPoints[i] = key;
			yPoints[i] = sortedInputMap.get(key);
			i++;
		}
		PolynomialSplineFunction linearInterpolatingFunction = new LinearInterpolator().interpolate(xPoints, yPoints);
		
		return linearInterpolatingFunction;
	}
	
}
