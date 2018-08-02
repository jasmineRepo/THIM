package jasmine.thim.data;

import microsim.data.MultiKeyCoefficientMap;
import microsim.data.excel.ExcelAssistant;
import microsim.engine.SimulationEngine;
import jasmine.thim.algorithms.LinearInterpolatingFunction;
import jasmine.thim.model.enums.CityType;
import jasmine.thim.model.enums.IncomeBaseGiniCoefficient;

import java.lang.reflect.Field;
import java.util.TreeMap;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.math.ArgumentOutsideDomainException;
import org.apache.commons.math.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;

import cern.jet.random.Normal;
import cern.jet.random.engine.MersenneTwister;


public class Parameters { 
	
	//Could define this explicitly here, however we now obtain this value from the maximum age specified in the mortality_rates.xls (which corresponds to a mortality rate of 1).  This takes place in the loadParameters() method.
	private static int MAX_AGE;			//The maximum age in the THIM paper is 100
	
	//cityParameters
	private static enum cityParameters {
		SimulatedNeighborhoods,
		StayPropIncDiff,
		MovePropIncDiff,
		EMean,
		ESigma,
		EBetaIncPar,
		EBetaIncNbhd,
		YBetaEduc,
		YBetaIncPar,
		YBetaIncNbhd,
		YSigma,
		HIncParm,
		MBetaH,
		MBetaIncNear;
	}
	private static int SimulatedNeighborhoods;		//Number of neighbourhoods in simulation
	private static double StayPropIncDiff,
						MovePropIncDiff,
						EMean,
						ESigma,
						EBetaIncPar,
						EBetaIncNbhd,
						YBetaEduc,
						YBetaIncPar,
						YBetaIncNbhd,
						YSigma,
						HIncParm,
						MBetaH,
						MBetaIncNear;

	
	private static MultiKeyCoefficientMap inputAgeIncomeProfile;	//To be interpolated to create a linear piecewise function, ageIncomeProfile
	private static MultiKeyCoefficientMap inputMortalityRates;		//To be interpolated to create a linear piecewise function, mortalityRates
	private static MultiKeyCoefficientMap incomeBaseDistMultiMap;
	private static MultiKeyCoefficientMap inputCityParameters;
	private static MultiKeyCoefficientMap lifetimeEarningsHealthHistogram;
	private static MultiKeyCoefficientMap healthDeltaDistMap;
	
	private static PolynomialSplineFunction propensityToMoveFunction;
	
	private static double[] ageIncomeProfile;
	private static double[] mortalityRates;
	private static double[] avgMortalityHazards;


	private static double[] healthDeltaEvents;	 
	private static double[] healthDeltaProbs;		
	private static double[] incomeBaseDistEvents;		
	private static double[] incomeBaseDistProbs;		
	private static Integer[] initialPopAgeDistEvents;
	private static double[] initialPopAgeDistProbs;	

	private static Normal standardNormal = new Normal(0., 1. ,
			new MersenneTwister(SimulationEngine.getRnd().nextInt()));
	
	public static void loadExternalParameters(CityType cityType, IncomeBaseGiniCoefficient giniCoeff) {
				
		incomeBaseDistMultiMap = ExcelAssistant.loadCoefficientMap("input/income_base_distribution.xls", "Sheet1", 1, 5);
		inputAgeIncomeProfile = ExcelAssistant.loadCoefficientMap("input/age_income_profile.xls", "Sheet1", 1, 1);
		inputMortalityRates = ExcelAssistant.loadCoefficientMap("input/mortality_rates.xls", "Sheet1", 1, 1);
		inputCityParameters = ExcelAssistant.loadCoefficientMap("input/city_parameters.xls", "Sheet1", 1, 6);
		lifetimeEarningsHealthHistogram = ExcelAssistant.loadCoefficientMap("input/LavgYLavgHForCompKeys.xls", "LavgYLavgHForComp", 4, 0);		//Load in the initial MultiKeyCoefficientMap with the appropriate MultiKeys.  We ignore the values.
		healthDeltaDistMap = ExcelAssistant.loadCoefficientMap("input/health_delta_distribution.xls", "Sheet1", 1, 1);
		
		//Obtain the maximum age possible for Sims, based on the mortality_rates.xls input file. 
		for(Object multiKey : inputMortalityRates.keySet()) {		//This provides a way of obtaining the maximum possible age from the mortality_rates.xls input file.  Beware that if the maximum age is greater than the ages in the other input files, there will be NullPointer exceptions when, for example, getting values from the age-income profile for Sims whose ages are not within the range in the age_income_profile.xls! 
			final int age = ((Number)((MultiKey) multiKey).getKey(0)).intValue();	
			if(age > MAX_AGE) {
				MAX_AGE = age;		
			}
		}
		MultiKey maxAgeMultiKey = new MultiKey(new Integer[] {MAX_AGE});
		if(((Number)inputMortalityRates.get(maxAgeMultiKey)).doubleValue() != 1.) {
			throw new RuntimeException("MAX_AGE is not properly set!  The highest age in the mortality_rates.xls input file does not correspond to a mortality rate of 1.  Please add a new line at the bottom of the existing values in /input/mortality_rates.xls that has a key (the first column) that represents the maximum age in the simulation, and a corresponding value (the second column) equal to 1.0.  The maximum age should also not be higher than the largest age specified in the age_income_profile.xls.");
		}

		ageIncomeProfile = new double[MAX_AGE + 1];
		mortalityRates = new double[MAX_AGE + 1];
		avgMortalityHazards = new double[MAX_AGE + 1];


		extractIncomeBaseEventsAndProbsArrays(giniCoeff);		//To work with MultiKeyCoefficientMap, so that variety of Gini coefficient income bases can easily be selected from GUI model parameters panel.
		extractHealthDeltaEventsAndProbsArrays();		//Convert map into arrays to be used with RegressionUtil#event() as the methods generally take arrays, and the extraction would otherwise have to take place at every iteration of the loop where the event() is called, and would therefore be very slow.
		
		calculateAgeIncomeProfile();			//Linear interpolation from intput .xls values
		calculateMortalityRates();				//Linear interpolation from intput .xls values
		calculateMortalityHazards();			//Should we automatically call that by placing this call inside calculateMortalityRates()?
		calculateInitialPopAgeDist();			//Calculates distribution of ages in initial population, given the mortality rates and a stable population (when sims are assumed to have average mortality hazard, ignoring health and income factors - i.e. when MBetaH and MBetaIncNear = 0)  
			
		setCityParameters(cityType);
		
		if(MBetaH < 0) {
			throw new IllegalArgumentException("MBetaH cannot be negative!  Check city_parameters.xls and change the value of MBetaH to a positive number or zero."); 
		}
		if(MBetaIncNear < 0) {
			throw new IllegalArgumentException("MBetaIncNear cannot be negative!  Check city_parameters.xls and change the value of MBetaIncNear to a positive number or zero."); 
		}


	}


	private static void calculateInitialPopAgeDist() {
		initialPopAgeDistEvents = new Integer[mortalityRates.length];			//Age
		initialPopAgeDistProbs = new double[initialPopAgeDistEvents.length];	//Prob of surviving to the end of this age
		
		initialPopAgeDistEvents[0] = 0;											//Age 0
		initialPopAgeDistProbs[0] = 1 - mortalityRates[0];		//Prob of surviving to the end of age 0
		double sum = initialPopAgeDistProbs[0];
		
		for(int age = 1; age <= MAX_AGE; age++) {
			initialPopAgeDistEvents[age] = age;
			initialPopAgeDistProbs[age] = (1 - mortalityRates[age]) * initialPopAgeDistProbs[age - 1];		//Prob of surviving to the end of this age = Prob of surviving to the end of the age a year less, multiplied by the survival rate for this age 
			sum += initialPopAgeDistProbs[age];
		}

		for(int age = 0; age <= MAX_AGE; age++) {
			initialPopAgeDistProbs[age] /= sum;						//Normalise probabilities
		}
		
	}
	
	private static void extractHealthDeltaEventsAndProbsArrays() {
		TreeMap<Double, Double> sortedHealthDeltaMap = new TreeMap<Double, Double>();
		for (Object multiKey : healthDeltaDistMap.keySet()) {
			final double key = ((Number)((MultiKey) multiKey).getKey(0)).doubleValue();
			final double value = ((Number)healthDeltaDistMap.getValue(multiKey)).doubleValue();
			sortedHealthDeltaMap.put(key, value);
		}
		healthDeltaEvents = new double[healthDeltaDistMap.size()];
		healthDeltaProbs = new double[healthDeltaEvents.length];
		int j = 0;
		for(Double key : sortedHealthDeltaMap.keySet()) {			//Careful!  Needs to iterate Map in correct order (this is guaranteed here because healthDeltaDistMap is a LinkedHashMap
			healthDeltaEvents[j] = key;
			healthDeltaProbs[j] = sortedHealthDeltaMap.get(key);
			j++;
		}
	}
	
	private static void extractIncomeBaseEventsAndProbsArrays(IncomeBaseGiniCoefficient giniCoeff) {
		TreeMap<Double, Double> sortedIncomeBaseMap = new TreeMap<Double, Double>();
		
		for (Object multiKey : incomeBaseDistMultiMap.keySet()) {
			final Double key = ((Number)((MultiKey) multiKey).getKey(0)).doubleValue();			//The values of the domain must be listed as the first key in the MultiKey
			final Double value = ((Number)incomeBaseDistMultiMap.getValue(key, giniCoeff.toString())).doubleValue();
			sortedIncomeBaseMap.put(key, value);
		}
		
		incomeBaseDistEvents = new double[incomeBaseDistMultiMap.size()];
		incomeBaseDistProbs = new double[incomeBaseDistEvents.length];
		int i=0;
		for (Double key : sortedIncomeBaseMap.keySet()) {			//Iterates in the correct order as TreeMap is used.
			incomeBaseDistEvents[i] = key.doubleValue();
			incomeBaseDistProbs[i] = sortedIncomeBaseMap.get(key);
			i++;
		}
	}

	public static void setCityParameters(CityType cityType) {
		for(cityParameters cityParameterName : cityParameters.values()) {
			try {
//Use of reflection to set parameters from city_parameters.xls file.  In order to add further fields, 
//the user must add the field names to the cityParameters enum and also to city_parameters.xls file
				Field field = Parameters.class.getDeclaredField(cityParameterName.toString());
				field.set(field, (Number)inputCityParameters.getValue(cityParameterName.toString(), cityType.toString()));
				
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		calculatePropensityToMoveFunction();	//Needs to be called only after Stay and MovePropIncDiff have been set (as they have been in this method)
	}
	
	public static void calculateAgeIncomeProfile() {

		PolynomialSplineFunction piecewiseLinearAgeIncomeFunction = LinearInterpolatingFunction.create(inputAgeIncomeProfile);
		
		for(int age = 0; age <= MAX_AGE; age++) {
			try {
				ageIncomeProfile[age] = piecewiseLinearAgeIncomeFunction.value(age);
			} catch (ArgumentOutsideDomainException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void calculateMortalityRates() {

		PolynomialSplineFunction piecewiseLinearMortalityFunction = LinearInterpolatingFunction.create(inputMortalityRates);
		
		for(int age = 0; age <= MAX_AGE; age++) {
			try {
				mortalityRates[age] =  piecewiseLinearMortalityFunction.value(age);
			} catch (ArgumentOutsideDomainException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	public static void calculateMortalityHazards() {
		for(int age = 0; age <= MAX_AGE; age++) {
			avgMortalityHazards[age] = -Math.log(1 - mortalityRates[age]);		//This leads to +Infinity when mortalityRate = 1 (clearly, if someone has a mortalityRate of 1, they should be removed from the simulation).  This is handled in Sim#considerDeath().		
		}
	}
	
	public static void calculatePropensityToMoveFunction() {	
		double[] xPoints = {0, StayPropIncDiff, MovePropIncDiff, Double.MAX_VALUE};		//StayPropIncDiff determines the percentage difference of income to nbhd average below which the Sim is perfectly happy staying in the nbhd.  MovePropIncDiff determines the percentage difference of income to nbhd average above which the Sim "tries with 100% effort" to move.  
		double[] yPoints = {0, 0, 1, 1};									//StayPropIncDiff and below have y values of 0.  MovePropIncDiff and above have y values of 1.
		setPropensityToMoveFunction(new LinearInterpolator().interpolate(xPoints, yPoints));
	}
	



	//////////////////////////////////////////////////////////////
	// Access methods
	//////////////////////////////////////////////////////////////	

	public static int getSimulatedNeighborhoods() {
		return SimulatedNeighborhoods;
	}
	
	public static double getEMean() {
		return EMean;
	}

	public static double getESigma() {
		return ESigma;
	}

	public static double getEBetaIncPar() {
		return EBetaIncPar;
	}

	public static double getEBetaIncNbhd() {
		return EBetaIncNbhd;
	}

	public static double getYBetaEduc() {
		return YBetaEduc;
	}

	public static double getYBetaIncPar() {
		return YBetaIncPar;
	}

	public static double getYBetaIncNbhd() {
		return YBetaIncNbhd;
	}

	public static double getYSigma() {
		return YSigma;
	}

	public static double getHIncParm() {
		return HIncParm;
	}

	public static double getMBetaH() {
		return MBetaH;
	}

	public static double getMBetaIncNear() {
		return MBetaIncNear;
	}

	public static Normal getStandardNormal() {
		return standardNormal;
	}

	public static PolynomialSplineFunction getPropensityToMoveFunction() {
		return propensityToMoveFunction;
	}

	public static void setPropensityToMoveFunction(
			PolynomialSplineFunction propensityToMoveFunction) {
		Parameters.propensityToMoveFunction = propensityToMoveFunction;
	}

	public static double[] getHealthDeltaEvents() {
		return healthDeltaEvents;
	}

	public static double[] getHealthDeltaProbs() {
		return healthDeltaProbs;
	}

	public static int getMaxAge() {
		return MAX_AGE;
	}

	public static double[] getIncomeBaseDistEvents() {
		return incomeBaseDistEvents;
	}

	public static double[] getIncomeBaseDistProbs() {
		return incomeBaseDistProbs;
	}

	public static Integer[] getInitialPopAgeDistEvents() {
		return initialPopAgeDistEvents;
	}

	public static double[] getInitialPopAgeDistProbs() {
		return initialPopAgeDistProbs;
	}


	public static double[] getAgeIncomeProfile() {
		return ageIncomeProfile;
	}


	public static double[] getMortalityRates() {
		return mortalityRates;
	}


	public static double[] getAvgMortalityHazards() {
		return avgMortalityHazards;
	}


	public static MultiKeyCoefficientMap getLifetimeEarningsHealthHistogram() {
		return lifetimeEarningsHealthHistogram;
	}


}
