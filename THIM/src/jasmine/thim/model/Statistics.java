package jasmine.thim.model;

import microsim.data.MultiKeyCoefficientMap;
import microsim.data.db.PanelEntityKey;
import microsim.engine.SimulationEngine;
import jasmine.thim.algorithms.LifetimeEarningsHealthSearch;
import jasmine.thim.data.Parameters;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

@Entity
public class Statistics {

	@Transient
	private THIMModel model
	= (THIMModel) SimulationEngine.getInstance().getManager(THIMModel.class.getCanonicalName());
	
	@Id
	private PanelEntityKey id;
	
	//Fields necessary for model evolution i.e. Sim processes
	
//	City statistics
	@Column(name="avg_adult_income")
	private double avgAdultIncome;	
	
	@Column(name="avg_health_index")
	private double avgHealth;	
	
	@Column(name="number_of_workers")
	private int numWorkingSims;				//Number of Sims in the simulation whose ages >= their yearsInEducation, i.e. they have finished school and are receiving an income.
	
	@Column(name="number_of_adults")
	private int numberOfAdultSims;
	
	@Transient
	private double[] avgAdultIncomeNearAge = new double[Parameters.getMaxAge() + 1];	//avg income of sims with age in [array index - model.getAgeBand(), array index + model.getAgeBand()], so it sums up the relevant ages in avgAdultIncomeForAge[] 
	
	@Transient
	private int[] numAdultSimsNearAge = new int[Parameters.getMaxAge() + 1];		//number of sims with age in [array index - model.getAgeBand(), array index + model.getAgeBand()], so it sums up the relevant ages of numAdultSimsForAge[]
			
//	Nbhd statistics
	@Transient
	private int[] numberOfWorkersInNbhd = new int[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private int[] numberOfAdultsInNbhd = new int[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] avgAdultIncomeInNbhd = new double[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] avgAdultIncomeInNbhdRelativeToCity = new double[Parameters.getSimulatedNeighborhoods()];

	//Field to produce statistics for THIM comparison (not necessary for simulation)
	@Column(name="number_of_children")
	private int numberOfChildSims = 0;			//Not needed for model evolution, only for outputting data to tables
	
	@Column(name="avg_years_in_education")
	private double avgYearsInEducation = 0.;	//Not needed for model evolution, only for outputting data to tables
	
	@Transient
	private double[] averageAgeAtDeath;  
	
	@Transient
	private int[] numSimsAgeAtDeath;
	
	@Transient
	private MultiKeyCoefficientMap lifetimeIncomeLifetimeHealthHistogram;

	@Transient
	private double[] nbhdOccupancyFactor = new double[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] avgNbhdEducation = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] avgNbhdHealthIndex = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private int[] numAdultResidentsInNbhd = new int[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private int[] numberOfChildrenInNbhd = new int[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] avgNbhdAge = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] averageHealthByAge;  
	
	@Transient
	private double[] averageIncomeByAge;  
	
	@Transient
	private int[] numSimsByAge;

	//For Avgs Table
	@Transient
	double longRunAvgYearsInEducation = 0.;
	
	@Transient
	double longRunAvgAdultIncome = 0.;
	
	@Transient
	double longRunAvgHealth = 0.;
	
	@Transient
	double longRunAvgAdultPopulation = 0.;
	
	@Transient
	double longRunAvgChildPopulation = 0.;
	
	@Transient
	int countAverageUpdates = 0;
	
	//For NbhdAvgs Table
	@Transient
	private double[] normalisedAvgNbhdAdultIncome = new double[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] normalisedAvgNbhdEducation = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] normalisedAvgNbhdHealthIndex = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNbhdAge = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNbhdEducation = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNbhdAdultIncome = new double[Parameters.getSimulatedNeighborhoods()];
	
	@Transient
	private double[] longRunAvgNbhdHealthIndex = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNbhdOccupancyFactor = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNumberOfAdultsInNbhd = new double[Parameters.getSimulatedNeighborhoods()];

	@Transient
	private double[] longRunAvgNumberOfChildrenInNbhd = new double[Parameters.getSimulatedNeighborhoods()];

	//Not necessary to run simulation - only for life expectancy chart in GUI.
	@Transient
	private int numSimsWhoDiedThisYear = 0;
	
	@Transient
	private double cumulativeAgeAtDeath = 0.;

	
	//////////////////////////////////////////////
	// Updating methods
	//////////////////////////////////////////////
	
	//Statistics without using JAS functionality - as can do it by only iterating through all Sims once, which is faster when large number of Sims are being simulated  
	protected void updateStatistics() {			//Is it worth having an if else test to check whether simulation time is in the record data to output table mode?  If it's before this time-period, could run with a simplified updateStatistics  
		
		int populationSize = model.getSims().size();
		if(populationSize == 0) {								//If no more sims, terminate simulation.  (Statistics will no longer exist with zero population.)
			long timeToComplete = System.currentTimeMillis() - model.getElapsedTime();
			THIMModel.getLog().info("Model complete - no more Sims exist.  Time since THIMModel#buildSchedule() called is " + timeToComplete + "ms.");			
			System.out.println("Model complete - no more Sims exist.  Time since THIMModel#buildSchedule() called is " + timeToComplete + "ms.");

			model.getEngine().pause();
		}
				
		int numberOfNbhds = Parameters.getSimulatedNeighborhoods();
		int maxAge = Parameters.getMaxAge();
		double ageBinInterval = model.getAgeBinInterval();		//For output tables with age_bins
		
		//Reset at start of calculation
		avgAdultIncome = 0.;
		avgHealth = 0.;
		avgYearsInEducation = 0.;
		numWorkingSims = 0;	
		numberOfAdultSims = 0;
		for(int i = 0; i < numberOfNbhds; i++) {
			//For model evolution
			numberOfWorkersInNbhd[i] = 0;
			numberOfAdultsInNbhd[i] = 0;
			avgAdultIncomeInNbhd[i] = 0.;
			avgNbhdHealthIndex[i] = 0.;					//Used in calculating city-wide avgHealth, which is necessary for death() process (so not just for output tables).
			
			//For output tables////////////////////////////////////////////////////////////////////////////
			avgNbhdEducation[i] = 0.;
			numberOfChildrenInNbhd[i] = 0;
			avgNbhdAge[i] = 0.;
			///////////////////////////////////////////////////////////////////////////////////////////////
			
		}
		for(int age = 0; age <= maxAge; age++) {

			numAdultSimsNearAge[age] = 0;	
			avgAdultIncomeNearAge[age] = 0.;
		}
	
		//Accumulate data from sims
		for(Sim sim: model.getSims()) {
			int nbhdId = sim.getNbhdId();
			int age = sim.getAge();
			double simEducation = sim.getYearsInEducation();			
			double simHealth = sim.getHealthIndex();
			
			avgNbhdHealthIndex[nbhdId] += simHealth;		//Used to calculate avgHealth, which is necessary for death() process (so not just for output tables)

			//For output tables//////////////////////////////////////////////////////////////////////////////////////
			int age_bin = (int)(sim.getAge() / ageBinInterval);		//This is designed on purpose to trunctate data by casting to integer.  This is subsequently used as an array index.		
			/////////////////////////////////////////////////////////////////////////////////////////////////////////
			
			//Workers only
			if(age >= simEducation) {		//If true, Sim has finished education and is therefore receiving an income
				
				numberOfWorkersInNbhd[nbhdId]++;
				
				double simIncome = sim.getIncome();				
				//Adults only
				if(age >= model.getMinAgeToReproduce()) {			//Sim is defined as an adult if true
					
					numberOfAdultsInNbhd[nbhdId]++;
					avgAdultIncomeInNbhd[nbhdId] += simIncome;
					
					int lowestAgeBin = Math.max(0, age - model.getAgeBand());			//Only includes adults in this calculation
					int highestAgeBin = Math.min(maxAge, age + model.getAgeBand());
					for(int ageIndex = lowestAgeBin; ageIndex <= highestAgeBin; ageIndex++) {
						avgAdultIncomeNearAge[ageIndex] += simIncome;
						numAdultSimsNearAge[ageIndex]++;
					}
				} 
				//For output tables//////////////////////////////////////////////////////////////////////////////////
				else numberOfChildrenInNbhd[nbhdId]++;		//For output tables
				/////////////////////////////////////////////////////////////////////////////////////////////////////
			} 
			//For output tables//////////////////////////////////////////////////////////////////////////////////////
			else numberOfChildrenInNbhd[nbhdId]++;		//For output tables

			avgNbhdAge[nbhdId] += age;		//For output tables
			avgNbhdEducation[nbhdId] += simEducation;		//For output tables
			
//			age_bin statistics			
			numSimsByAge[age_bin]++;		//For output tables with age_bins
			averageHealthByAge[age_bin] += simHealth;		//For output tables with age_bins
			averageIncomeByAge[age_bin] += sim.getIncome();		//For output tables with age_bins
			/////////////////////////////////////////////////////////////////////////////////////////////////////////
			
		}
		
		//Now process information accumulated from all sims
		
		for(int age = 0; age <= maxAge; age++) {
			if(numAdultSimsNearAge[age] > 0) {
				avgAdultIncomeNearAge[age] /= (double)numAdultSimsNearAge[age];		//Now we divide by number of sims to get the average.
			}
		}

		//Aggregate NBHD measures to form City averages (Note - nbhd measures are not yet divided by nbhd sizes here!).  These are subsequently divided by city-wide measures (e.g. total number of adults, total number of workers, total population size etc. to get correct amount).
		for(int nbhdId = 0; nbhdId < model.getNbhds().size(); nbhdId++) {

			numWorkingSims += numberOfWorkersInNbhd[nbhdId];
			
			int numberOfAdultsInThisNbhd = numberOfAdultsInNbhd[nbhdId];
			if(numberOfAdultsInThisNbhd > 0) {
				avgAdultIncome += avgAdultIncomeInNbhd[nbhdId];						//This is taken before being divided by numberOfAdultsInThisNbhd, so is summing up total adult income and does not need to be multiplied by the number of adult sims in the nbhd.  It will be divided by total adult population below.
				avgAdultIncomeInNbhd[nbhdId] /= (double)numberOfAdultsInThisNbhd;	//Now we divide by number of Adults in nbhd to get final result
				
				numberOfAdultSims += numberOfAdultsInThisNbhd;
			}
			avgHealth += avgNbhdHealthIndex[nbhdId];				//Necessary for death calculation
			
			//For output tables///////////////////////////////////////////////////////////////////////
			avgYearsInEducation += avgNbhdEducation[nbhdId];
			//////////////////////////////////////////////////////////////////////////////////////////
		}
		
		//Calculations involving city-wide measures (e.g. avgAdultIncome, total number of workers, total number of adults, population size)
		if(numberOfAdultSims > 0) {
			avgAdultIncome /= (double)numberOfAdultSims;	
		}
		avgHealth /= (double)populationSize;		//Already checked populationSize != 0 at beginning of updateStatistics()
		
		//For output tables////////////////////////////////////////////////////////////////////////////
		avgYearsInEducation /= (double)populationSize;		//Already checked populationSize != 0 at beginning of updateStatistics()
		numberOfChildSims = populationSize - numberOfAdultSims;		
		///////////////////////////////////////////////////////////////////////////////////////////////
		
		for(Nbhd nbhd : model.getNbhds()) {
			int nbhdId = nbhd.getId().getId().intValue();
			
			double avgAdultIncomeInThisNbhd = avgAdultIncomeInNbhd[nbhdId];
			if(avgAdultIncome > 0) {
				avgAdultIncomeInNbhdRelativeToCity[nbhdId] = avgAdultIncomeInThisNbhd / avgAdultIncome;
			}
			
			//Set fields in Nbhd class:
			nbhd.setAvgNbhdAdultIncome(avgAdultIncomeInThisNbhd);
			nbhd.setNbhdAdultIncomeRelativeToCityAvg(avgAdultIncomeInNbhdRelativeToCity[nbhdId]);
			nbhd.setNumWorkingResidents(numberOfWorkersInNbhd[nbhdId]);


			//For output tables/////////////////////////////////////////////////////////////////////////
			if(numWorkingSims > 0) {
				nbhdOccupancyFactor[nbhdId] = (double)numberOfWorkersInNbhd[nbhdId] * ((double)Parameters.getSimulatedNeighborhoods() / (double)numWorkingSims);
			}
			int numSimsInNbhd = numberOfAdultsInNbhd[nbhdId] + numberOfChildrenInNbhd[nbhdId];
			if(numSimsInNbhd > 0) {
				avgNbhdAge[nbhdId] /= (double)numSimsInNbhd;						
				avgNbhdHealthIndex[nbhdId] /= (double)numSimsInNbhd;				
				avgNbhdEducation[nbhdId] /= (double)numSimsInNbhd;	
				
			}
			
			//Set fields in Nbhd class (For output tables)
			if(!SimulationEngine.getInstance().isSilentMode()) {		//No need to persist in nbhd entity if database option is turned off!
				nbhd.setAvgNbhdAge(avgNbhdAge[nbhdId]);
				nbhd.setAvgNbhdEducation(avgNbhdEducation[nbhdId]);
				nbhd.setAvgNbhdHealthIndex(avgNbhdHealthIndex[nbhdId]);
				nbhd.setNbhdOccupancyFactor(nbhdOccupancyFactor[nbhdId]);
				nbhd.setNumAdultResidents(numberOfAdultsInNbhd[nbhdId]);
				nbhd.setNumChildResidents(numberOfChildrenInNbhd[nbhdId]);
			}
			/////////////////////////////////////////////////////////////////////////////////////////////
			
		}
				
	}
	
	public void incrementCityAndNbhdAverages() {
				
		longRunAvgYearsInEducation += avgYearsInEducation;
		longRunAvgAdultIncome += avgAdultIncome;
		longRunAvgHealth += avgHealth;
		longRunAvgAdultPopulation += numberOfAdultSims;
		longRunAvgChildPopulation += numberOfChildSims;
		
		for(int nbhdId = 0; nbhdId < Parameters.getSimulatedNeighborhoods(); nbhdId++) {
			longRunAvgNbhdAge[nbhdId] += avgNbhdAge[nbhdId];
			longRunAvgNbhdEducation[nbhdId] += avgNbhdEducation[nbhdId];
			longRunAvgNbhdHealthIndex[nbhdId] += avgNbhdHealthIndex[nbhdId];
			longRunAvgNbhdOccupancyFactor[nbhdId] += nbhdOccupancyFactor[nbhdId];
			longRunAvgNumberOfAdultsInNbhd[nbhdId] += numberOfAdultsInNbhd[nbhdId];
			longRunAvgNumberOfChildrenInNbhd[nbhdId] += numberOfChildrenInNbhd[nbhdId];
			longRunAvgNbhdAdultIncome[nbhdId] += avgAdultIncomeInNbhd[nbhdId];
		}
		
		countAverageUpdates++;			
	}

	public void calculateLongRunCityAndNbhdStatistics() {
		
		longRunAvgYearsInEducation /= (double)countAverageUpdates;		//Is this how the averages are calculated?
		longRunAvgAdultIncome /= (double)countAverageUpdates;		//Is this how the averages are calculated?
		longRunAvgHealth /= (double)countAverageUpdates;		//Is this how the averages are calculated?
		longRunAvgAdultPopulation /= (double)countAverageUpdates;		//Is this how the averages are calculated?
		longRunAvgChildPopulation /= (double)countAverageUpdates;		//Is this how the averages are calculated?
		
		for(int nbhdId = 0; nbhdId < Parameters.getSimulatedNeighborhoods(); nbhdId++) {
			longRunAvgNbhdAge[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNbhdEducation[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNbhdHealthIndex[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNbhdOccupancyFactor[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNumberOfAdultsInNbhd[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNumberOfChildrenInNbhd[nbhdId] /= (double)countAverageUpdates;
			longRunAvgNbhdAdultIncome[nbhdId] /= (double)countAverageUpdates;

			//This method of calculating the normalised Avgs is consistent with the ModGen tables, in that the normalised Nbhd Avgs = nbhd avg / city avg, where the city avg is from the Avgs table.
			//However, it is not clear whether the correct way of calculating the City Avgs is the way we are doing it here - incrementing each year and dividing by the number of increments.  If this is not correct, then the following normalised fields will also not be correct.
			normalisedAvgNbhdEducation[nbhdId] = longRunAvgNbhdEducation[nbhdId] / longRunAvgYearsInEducation;
			normalisedAvgNbhdHealthIndex[nbhdId] = longRunAvgNbhdHealthIndex[nbhdId] / longRunAvgHealth;
			normalisedAvgNbhdAdultIncome[nbhdId] = longRunAvgNbhdAdultIncome[nbhdId] / longRunAvgAdultIncome;
		}
		
	}
			
	public void recordStatisticsAtDeath(			//Can be called at any time-step
			double cumulativeHealthIndex, double cumulativeIncome,
			double ageAtDeath) {

		//For output tables
		if(SimulationEngine.getInstance().getTime() >= model.getRecordDataAfterYear()) {
			int age_bin = (int) (ageAtDeath / model.getAgeBinInterval());
			averageAgeAtDeath[age_bin] += ageAtDeath;
			numSimsAgeAtDeath[age_bin]++;
			
			final double epsilon = 1.e-15;
			double lifetimeAverageHealthIndex = cumulativeHealthIndex / ageAtDeath;
			//Necessary owing to precision issues
			if(lifetimeAverageHealthIndex > model.getMaxHealthIndex()) {						
				lifetimeAverageHealthIndex = model.getMaxHealthIndex();
			}
			else if(lifetimeAverageHealthIndex < model.getMinHealthIndex()) {
				lifetimeAverageHealthIndex = model.getMinHealthIndex() + epsilon;		//Sims cannot have lifetimeAverageHealthIndex = 0 as they must be initialised with a positive health index.
			}
			double lifetimeAverageEarnings = cumulativeIncome / ageAtDeath;		

			LifetimeEarningsHealthSearch.incrementValue(lifetimeIncomeLifetimeHealthHistogram, lifetimeAverageEarnings, lifetimeAverageHealthIndex);
			
		}
		
		cumulativeAgeAtDeath += ageAtDeath;		
		numSimsWhoDiedThisYear++;
		
	}
		
	public void calculateStatisticsByAgeBins() {			//Called when scheduled by collector
		for(int age_bin = 0; age_bin < model.getNumAgeBinsInTables() + 1; age_bin++) {
			averageAgeAtDeath[age_bin] /= (double)numSimsAgeAtDeath[age_bin];	
			averageHealthByAge[age_bin] /= (double)numSimsByAge[age_bin];			//Need to array for these quantities to be reset after exporting to a .csv to allow for recording over another time interval
			averageIncomeByAge[age_bin] /= (double)numSimsByAge[age_bin];
		}
	}

	public void resetStatisticsForOutputTables() {			//Use at time recordDataAfterYear to clear previous stats (probably quicker to do this, than check at every year whether we should record data!?

		//For Avgs table
		longRunAvgYearsInEducation = 0.;
		longRunAvgAdultIncome = 0.;
		longRunAvgHealth = 0.;
		longRunAvgAdultPopulation = 0.;
		longRunAvgChildPopulation = 0.;
		countAverageUpdates = 0;

		//For NbhdAvgs table
		for(int nbhdId = 0; nbhdId < Parameters.getSimulatedNeighborhoods(); nbhdId++) {
			longRunAvgNbhdAge[nbhdId] = 0.;
			longRunAvgNbhdEducation[nbhdId] = 0.;
			longRunAvgNbhdAdultIncome[nbhdId] = 0.;
			longRunAvgNbhdHealthIndex[nbhdId] = 0.;
			longRunAvgNbhdOccupancyFactor[nbhdId] = 0.;
			longRunAvgNumberOfAdultsInNbhd[nbhdId] = 0.;
			longRunAvgNumberOfChildrenInNbhd[nbhdId] = 0.;
			normalisedAvgNbhdEducation[nbhdId] = 0.;		//Is it necessary to reset the normalised measures, as they are only created by assignment, not through incrementing...?
			normalisedAvgNbhdHealthIndex[nbhdId] = 0.;
			normalisedAvgNbhdAdultIncome[nbhdId] = 0.;
		}
		
		//For Age Bin tables
		for(int age_bin = 0; age_bin < model.getNumAgeBinsInTables() + 1; age_bin++) {
			averageAgeAtDeath[age_bin] = 0.;			//Probably not needed, as recordStatisticsAtDeath() checks the time to decide whether to update this statistical object
			numSimsAgeAtDeath[age_bin] = 0;				//Probably not needed, as recordStatisticsAtDeath() checks the time to decide whether to update this statistical object
			averageHealthByAge[age_bin] = 0.;  			
			averageIncomeByAge[age_bin] = 0.;  
			numSimsByAge[age_bin] = 0;
		}
		
		//For lifetimeIncomeAndHealth table
		for(Object multiKey : lifetimeIncomeLifetimeHealthHistogram.keySet()) {					//Probably not needed, as recordStatisticsAtDeath() checks the time to decide whether to update this statistical object
			lifetimeIncomeLifetimeHealthHistogram.put(multiKey, 0);				//Initialise values to 0
		}

	}
	
	protected void initialiseStatisticsArrays() {

		//For lifetimeIncomeAndHealth tables
		lifetimeIncomeLifetimeHealthHistogram = Parameters.getLifetimeEarningsHealthHistogram();		//Set to MultiKeyCoefficientMap loaded in externally from LavgYLavgHForComp.xls file.
		for(Object multiKey : lifetimeIncomeLifetimeHealthHistogram.keySet()) {
			lifetimeIncomeLifetimeHealthHistogram.put(multiKey, 0);				//Initialise values to 0
		}

		//For age bin tables
		averageAgeAtDeath = new double[model.getNumAgeBinsInTables() + 1];		//+1 so that if max age = 100, if there are e.g. 21 bins, the first 20 bins contain ages in intervals of 5 years: [0,4], [5, 9], ....[95, 99], however we still need a bin for {100} in case there are some Sims who make it to the max age and are due to die, but not on their birthday.  The array index is the identity of the age_bin, i.e. age_bin = (int)(age of sim / numYearsInTableBin)  
		numSimsAgeAtDeath = new int[model.getNumAgeBinsInTables() + 1];		//+1 so that if max age = 100, if there are e.g. 21 bins, the first 20 bins contain ages in intervals of 5 years: [0,4], [5, 9], ....[95, 99], however we still need a bin for {100} in case there are some Sims who make it to the max age and are due to die, but not on their birthday
		averageHealthByAge = new double[model.getNumAgeBinsInTables() + 1];		//+1 so that if max age = 100, if there are e.g. 21 bins, the first 20 bins contain ages in intervals of 5 years: [0,4], [5, 9], ....[95, 99], however we still need a bin for {100} in case there are some Sims who make it to the max age and are due to die, but not on their birthday.  The array index is the identity of the age_bin, i.e. age_bin = (int)(age of sim / numYearsInTableBin)  
		averageIncomeByAge = new double[model.getNumAgeBinsInTables() + 1];		//+1 so that if max age = 100, if there are e.g. 21 bins, the first 20 bins contain ages in intervals of 5 years: [0,4], [5, 9], ....[95, 99], however we still need a bin for {100} in case there are some Sims who make it to the max age and are due to die, but not on their birthday.  The array index is the identity of the age_bin, i.e. age_bin = (int)(age of sim / numYearsInTableBin)   
		numSimsByAge = new int[model.getNumAgeBinsInTables() + 1];		//+1 so that if max age = 100, if there are e.g. 21 bins, the first 20 bins contain ages in intervals of 5 years: [0,4], [5, 9], ....[95, 99], however we still need a bin for {100} in case there are some Sims who make it to the max age and are due to die, but not on their birthday

		for(int age_bin = 0; age_bin < model.getNumAgeBinsInTables() + 1; age_bin++) {
			averageAgeAtDeath[age_bin] = 0.;
			numSimsAgeAtDeath[age_bin] = 0;
			averageHealthByAge[age_bin] = 0.;  			
			averageIncomeByAge[age_bin] = 0.;  
			numSimsByAge[age_bin] = 0;
		}
		
		//For NbhdAvgs table
		for(int nbhdId = 0; nbhdId < Parameters.getSimulatedNeighborhoods(); nbhdId++) {
			longRunAvgNbhdAge[nbhdId] = 0.;
			longRunAvgNbhdEducation[nbhdId] = 0.;
			longRunAvgNbhdAdultIncome[nbhdId] = 0.;
			longRunAvgNbhdHealthIndex[nbhdId] = 0.;
			longRunAvgNbhdOccupancyFactor[nbhdId] = 0.;
			longRunAvgNumberOfAdultsInNbhd[nbhdId] = 0.;
			longRunAvgNumberOfChildrenInNbhd[nbhdId] = 0.;
			normalisedAvgNbhdEducation[nbhdId] = 0.;		
			normalisedAvgNbhdHealthIndex[nbhdId] = 0.;
			normalisedAvgNbhdAdultIncome[nbhdId] = 0.;
		}
	}
	
	public double getLifeExpectancyAndReset() {			//For GUI
		double lifeExpectancy = (cumulativeAgeAtDeath / (double)numSimsWhoDiedThisYear);
		cumulativeAgeAtDeath = 0.;
		numSimsWhoDiedThisYear = 0;
		return lifeExpectancy;
	}

	///////////////////////////////////////////////
	// Access methods
	///////////////////////////////////////////////	
	
	public double getAvgAdultIncome() {
		return avgAdultIncome;
	}
	public double getAvgHealth() {
		return avgHealth;
	}
	public int getNumWorkingSims() {
		return numWorkingSims;
	}
	public double getAvgAdultIncomeNearAge(int age) {
		return avgAdultIncomeNearAge[age];			
	}

	public double[] getAverageAgeAtDeath() {
		return averageAgeAtDeath;
	}

	public int[] getNumSimsAgeAtDeath() {
		return numSimsAgeAtDeath;
	}

	public double[] getAverageHealthByAge() {
		return averageHealthByAge;
	}

	public double[] getAverageIncomeByAge() {
		return averageIncomeByAge;
	}

	public int[] getNumSimsByAge() {
		return numSimsByAge;
	}

	public MultiKeyCoefficientMap getLifetimeIncomeLifetimeHealthHistogram() {
		return lifetimeIncomeLifetimeHealthHistogram;
	}

	public double getLongRunAvgYearsInEducation() {
		return longRunAvgYearsInEducation;
	}

	public double getLongRunAvgAdultIncome() {
		return longRunAvgAdultIncome;
	}

	public double getLongRunAvgHealth() {
		return longRunAvgHealth;
	}

	public double getLongRunAvgAdultPopulation() {
		return longRunAvgAdultPopulation;
	}

	public double getLongRunAvgChildPopulation() {
		return longRunAvgChildPopulation;
	}

	public double[] getNormalisedAvgNbhdAdultIncome() {
		return normalisedAvgNbhdAdultIncome;
	}

	public double[] getNormalisedAvgNbhdEducation() {
		return normalisedAvgNbhdEducation;
	}

	public double[] getNormalisedAvgNbhdHealthIndex() {
		return normalisedAvgNbhdHealthIndex;
	}

	public double[] getLongRunAvgNbhdAge() {
		return longRunAvgNbhdAge;
	}

	public double[] getLongRunAvgNbhdEducation() {
		return longRunAvgNbhdEducation;
	}

	public double[] getLongRunAvgNbhdAdultIncome() {
		return longRunAvgNbhdAdultIncome;
	}

	public double[] getLongRunAvgNbhdHealthIndex() {
		return longRunAvgNbhdHealthIndex;
	}

	public double[] getLongRunAvgNbhdOccupancyFactor() {
		return longRunAvgNbhdOccupancyFactor;
	}

	public double[] getLongRunAvgNumberOfAdultsInNbhd() {
		return longRunAvgNumberOfAdultsInNbhd;
	}

	public double[] getLongRunAvgNumberOfChildrenInNbhd() {
		return longRunAvgNumberOfChildrenInNbhd;
	}

}
