package jasmine.thim.model;

import microsim.annotation.ModelParameter;
import microsim.engine.AbstractSimulationManager;
import microsim.engine.SimulationEngine;
import microsim.event.EventListener;
import microsim.event.Order;
import microsim.event.SingleTargetEvent;
import microsim.gui.shell.MicrosimShell;
import microsim.statistics.regression.RegressionUtils;
import jasmine.thim.data.Parameters;
import jasmine.thim.experiment.THIMStart;
import jasmine.thim.model.enums.CityType;
import jasmine.thim.model.enums.IncomeBaseGiniCoefficient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import org.apache.log4j.Logger;


public class THIMModel extends AbstractSimulationManager implements EventListener {
	
	private final static Logger log = Logger.getLogger(THIMModel.class);
	
	//---------------------------------------------------------------------------------------------------------------
	// MODEL PARAMETERS - controllable via the GUI	
	//---------------------------------------------------------------------------------------------------------------
			
	//Parameters for model evolution
	
	@ModelParameter(description="Type of city, either C, U (with/out Mortality Factors) or ModGen")		//As defined in the THIM paper.  Now there are also options C_NoMortalityFactors and U_NoMortalityFactors to turn off health and income factors in determining mortality, to test whether the population is stable in these cases and with the specific fertilityHazard calculated.
	@Enumerated(EnumType.STRING)
	private CityType cityType = CityType.U;
	
	@ModelParameter(description="Gini coefficient for the Income Base distribution")		//Values from 'THIM parms aq.xls' file from Wolfson et al.
	@Enumerated(EnumType.STRING)
	private IncomeBaseGiniCoefficient incomeBaseGiniCoeff = IncomeBaseGiniCoefficient.Gini_0_570; 
	
	@ModelParameter(description="Number of years to run simulation")
	private Integer endYear = 500;			//In the paper and ModGen, the simulation spans 500 years.

//	//TODO: loadInitialPopulationFromDatabase not ready yet!
////	@ModelParameter(description="Toggle to choose whether to load initial population from database or generate within the model")
//	private Boolean loadInitialPopulationFromDatabase = false;

	@ModelParameter(description="Initial population size")
	private Integer startingPopulationSize = THIMStart.getInitialPopulationSize();			//In paper and ModGen, around 50,000 Sims were used.  A reasonable starting value in our implementation (in terms of speed) is 10,000.
	
	@ModelParameter(description="Minimum age to give birth (should be greater or equal to maxYearsOfEducation)")
	private Integer minAgeToReproduce = 20;		//This value is used as an example in the paper.												
	
	@ModelParameter(description="Maximum age to give birth (greater than minAgeToReproduce and less than 100)")
	private Integer maxAgeToReproduce = 40;		//This value is used as an example in the paper.
	
	@ModelParameter(description="Minimum years in education (before receiving an income and being able to move neighbourhood)")
	private Integer minYearsOfEducation = 1;	//This value is used as an example in the paper.

	@ModelParameter(description="Maximum years in education (before receiving an income and being able to move neighbourhood)")
	private Integer maxYearsOfEducation = 20;	//This value is used as an example in the paper.
	
	@ModelParameter(description="Minimum base income reflecting innate ability and permanent income")
	private Double minBaseIncome = 0.1;		//Distribution in ModGen has domain exp(-2.8) to exp(2.8), so 0.061 to 16.44.  However, ModGen parameters uploaded on March 1st 2015 to the uSask GitLab server restrict this to [0.1, 15] (thim-master/modgen/src/Base(Parameters).dat), so we are cutting off the ends of the distribution!
//	private Double minBaseIncome = 0.06;
	
	@ModelParameter(description="Maximum base income reflecting innate ability and permanent income")
	private Double maxBaseIncome = 15.;		//Distribution in ModGen has domain exp(-2.8) to exp(2.8), so 0.061 to 16.44.  However, ModGen parameters uploaded on March 1st 2015 to the uSask GitLab server restrict this to [0.1, 15] (thim-master/modgen/src/Base(Parameters).dat), so we are cutting off the ends of the distribution!
//	private Double maxBaseIncome = 16.5;
	
	@ModelParameter(description="Age band describes the interval (a +/- AgeBand) which determines "
			+ "the comparison group in avgIncomeNearAge")
	private Integer ageBand = 10;				//This value is used as an example in the paper.
	
	@ModelParameter(description="If MaxOccupancyFactor > 1, there is always a nbhd with space in "
			+ "which the sim can move (if it is preferable????)")
	private Double maxOccupancyFactor = 4.;		//This value is used as an example in the paper
	
	@ModelParameter(description="Choose whether to fix the Random Number seed")
	private Boolean fixRandomSeed = false;
	
	@ModelParameter(description="Choose whether to fix the Random Number seed")
	private Integer randomSeedIfFixed = 0;

	//Bonus parameters - not specified in the THIM document
	@ModelParameter(description="Minimum possible healthIndex value")			//Could make into model parameter that can be specified in the GUI if desired
	private Double minHealthIndex = 0.;		//Paper specifies this as 0
	
	@ModelParameter(description="Maximum possible healthIndex value")			//Could make into model parameter that can be specified in the GUI if desired
	private Double maxHealthIndex = 1.;		//Paper specifies this as 1 (which is what new born Sims are endowed with)
	
	//Parameters to configure output tables - not for model evolution.
	
	@ModelParameter(description="year in which to start collecting data to produce output tables")
	private Integer recordDataAfterYear = 450;		
	
	@ModelParameter(description="number of age bins")
	private Integer numAgeBinsInTables = 20;			
	
	private double ageBinInterval;				//The number of years of age range each bin contains (== max age / numAgeBinsInTables)
	
	public boolean microsimShellUse;			//Flag effects the way the simulation terminates to allow for batch mode and MultiRun mode. 

	
	//-----------------------------------------------------------------------------------------------------
	// Fields
	//-----------------------------------------------------------------------------------------------------
	
	
	private Set<Sim> sims;
	
	private List<Nbhd> nbhds;			//TODO: Consider removing and replace by arrays of info in model class 
	
	private Statistics stats;		//Create a Statistics class to hold the aggregate statistics (and we can persist these in the database)
	
	private double fertilityHazard;			//Calculated once initial population has been created
	
	private long elapsedTime;// = System.currentTimeMillis();		//For measuring real-time to between for model to build and to complete the simulation.
	
	/////////////////////////////////////////////////////////////////////////
	// Manager
	/////////////////////////////////////////////////////////////////////////
	
	public void buildObjects() {
		
		elapsedTime = System.currentTimeMillis();		//For measuring real-time to between for model to build and to complete the simulation.
		
		System.out.println("\nInitial population size is " + startingPopulationSize);
		
			
		///////////////////////////////Initialization and Parameters/////////////////////////////////////////
		//Done here so that the Model Parameters from the GUI will correctly set the other parameters and initial capacities of collections		
		if(fixRandomSeed) {
			SimulationEngine.getRnd().setSeed(randomSeedIfFixed);			
		}
		else {
			SimulationEngine.getRnd().setSeed(System.currentTimeMillis());			//Use current time as seed for random number generator (the default for java.util.Random by default initialized to System.currentTimeMillis()) 
		}

		Parameters.loadExternalParameters(cityType, incomeBaseGiniCoeff);
		checkParameters();				//Ensure bounded parameters are consistent (see page 10 of extended THIM paper)
		
		//Initialise parameters here after model parameters have been set, so that GUI can properly influence the initial capacity of collections like ArrayLists and HashMaps, and the value of other parameters
		
		stats = new Statistics();		//Create a Statistics class to hold the aggregate statistics (and we can persist these in the database)
		
		sims = new HashSet<Sim>((int)(startingPopulationSize.doubleValue() / 0.75));		//Default load factor is 0.75, so set initial capacity to size / load factor TODO: check if necessary to improve Hash performance
//		sims = new LinkedHashSet<Sim>((int)(startingPopulationSize.doubleValue() / 0.75));		//Default load factor is 0.75, so set initial capacity to size / load factor TODO: check if necessary to improve Hash performance
		
		int numberOfNbhds = Parameters.getSimulatedNeighborhoods();
		nbhds = new ArrayList<Nbhd>(numberOfNbhds);
					
		////////////////////////////////////////Agents/////////////////////////////////////////////
				
		for(int i=0; i < numberOfNbhds; i++) {
			nbhds.add(new Nbhd((Nbhd.nbhdIdCounter)++));
		}
		
		//Create sims and associate with neighbourhoods
//		if(loadInitialPopulationFromDatabase) {
//			System.out.println("Loading initial population from database.  Note that the 'Starting population size' model parameter will have no effect!");
//			ArrayList<Sim> simsArrayList = (ArrayList<Sim>) DatabaseUtils.loadTable(Sim.class);			//For when there is an input database of data to load in.  Note, still need to assign nbhds after loading in, as we cannot know a priori how many nbhds there are.
//			sims = new HashSet<Sim>(simsArrayList);			//Need to convert ArrayList to LinkedList for faster simulation speed
//		}
//		else {				
			//When there is no input database of data to load in.
		
		//For establishing parent-child links:
			double proportionOfChildSims = 0.;		//Will be larger than the proportion of child sims looking for parents if we restrict the latter to those still in education (i.e. age < yearsInEducation <= minAgeToReproduce)
			for(int age = 0; age < minAgeToReproduce; age++) {
				proportionOfChildSims += Parameters.getInitialPopAgeDistProbs()[age];
			}
			double expectedInitialNumberOfAdultSims = (1. - proportionOfChildSims) * startingPopulationSize.doubleValue();
			List<Sim> childSimsLookingForAParent = new ArrayList<Sim>((int)(startingPopulationSize.doubleValue() * proportionOfChildSims / 0.9));		// Divide by 0.9 to allow a larger capacity than expected 

			List<ArrayList<ArrayList<Sim>>> potentialParents = new ArrayList<ArrayList<ArrayList<Sim>>>(numberOfNbhds);		//First index is by nbhdId, second index is child sims age (we need to filter out parents whose age > maxAgeToReproduce + child.age; they can be parents for older children, but perhaps not for the younger children).  The result is an arraylist of all potential parent sims for the specific child in question.
			for(int nbhdId = 0; nbhdId < numberOfNbhds; nbhdId++) {
				potentialParents.add(new ArrayList<ArrayList<Sim>>(maxYearsOfEducation));
				for(int potentialChildAge = 0; potentialChildAge < maxYearsOfEducation; potentialChildAge++) {
					potentialParents.get(nbhdId).add(new ArrayList<Sim>((int)(expectedInitialNumberOfAdultSims / (double)numberOfNbhds)));		//No need to include default load factor in denominator here, as the number of parent sims will be substantially less than the adult population size due to older sims being unable to still have parent-child links (i.e. any sims they gave birth to are now adults).  
				}
			}
			
			int maxParentAge = maxAgeToReproduce + maxYearsOfEducation - 1;		//If maxAgeToReproduce = 40 and maxYearsOfEducation = 20, then maxParentAge = 59 (at age 60, the child-parent link should have been broken as child has broken the links when they finish education at some age up to maxYearsOfEducation)
			HashMap<Integer, Integer> minPotentialChildAge = new HashMap<Integer, Integer>(maxParentAge + 1 - minAgeToReproduce);
			HashMap<Integer, Integer> maxPotentialChildAge = new HashMap<Integer, Integer>(maxParentAge + 1 - minAgeToReproduce);
			for(int parentAge = minAgeToReproduce; parentAge <= maxParentAge; parentAge++) {
				minPotentialChildAge.put(parentAge, Math.max(0, parentAge - maxAgeToReproduce));			//Bounded as parent is too old for any children younger than this		
				maxPotentialChildAge.put(parentAge, Math.min(maxYearsOfEducation - 1, parentAge - minAgeToReproduce));		//Bounded as parent is too young for any children older than this 
			}
			
		//Create starting population
			for(int i=0; i < startingPopulationSize; i++) {
				Sim initialSim = new Sim((Sim.simIdCounter)++);			 
				initialSim.configureInitialSimPropertiesAndSchedule();			//Birth'day's (birthTimestamps) are randomly uniformly distributed across year
				sims.add(initialSim);
				int initialSimAge = initialSim.getAge();
				int initialSimNbhdId = initialSim.getNbhdId();
				//The THIM paper specifies to find parents for all sims aged < minAgeToReproduce, however this is inconsistent with the idea that Sims can move nbhd and earn an income when their age reaches yearsInEducation.  The only impact child-parent links have is to force the child to move when the parent moves, so why maintain child-parent links that would not be maintained during the simulation?  Surely it is better to only look for child-parent links for sims that are still in education!  This is our approach here.  
				if(initialSimAge < initialSim.getYearsInEducation()) {		//If true, initial Sim is a child, so need to establish link with a parent (TODO: do we restrict this to only Sims in the same nbhd, or force the child sim to move to the nbhd where a parent is found?).
					childSimsLookingForAParent.add(initialSim);
				}
				else if( (initialSimAge <= maxParentAge) && (initialSimAge >= minAgeToReproduce) )	{	//A candidate parent - note that it is possible for a parent to have child-parent links up to (and including) the age of maxYearsOfEducation + maxAgeToReproduce - 1, as the sim could be at maxAgeToReproduce when they give birth to a Sim whose yearsInEducation = maxYearsOfEducation 
					
					for(int potentialChildAge = minPotentialChildAge.get(initialSimAge); potentialChildAge <= maxPotentialChildAge.get(initialSimAge); potentialChildAge++) {
						potentialParents.get(initialSimNbhdId).get(potentialChildAge).add(initialSim);
					}
				}
			}
			
		//Establish parent-child links - there are two differences from example suggested in THIM paper:
			//1) Allows for parent to have more than one child born in same year (just like in the simulation - so no inconsistency!)
			//2) It is possible (though unlikely for sizable populations) that there is no potential parent in the same nbhd of an appropriate age for the child.  In this case, we assume the parent has already died and the child is an orphan - which is possible during the simulation, though precluded in the model initialisation implementation suggested in the THIM paper.
			for(Sim child : childSimsLookingForAParent) {	
				if(!potentialParents.get(child.getNbhdId()).get(child.getAge()).isEmpty()) {
					Sim parent = RegressionUtils.event(potentialParents.get(child.getNbhdId()).get(child.getAge()), SimulationEngine.getRnd());		//Randomly samples a parent over the space of all possible parents in the same nbhd as the child sim and of an appropriate age (i.e. minAgeToReproduce <= parent age - child age < maxAgeToReproduce).  If no potential parents exist, the child is an orphan.
					child.setParent(parent);
					parent.getChildSims().add(child);
				}
//				else System.out.println("Child " + child.getId().getId() + " in nbhd ," + child.getNbhdId() + ", with age, " + child.getAge() + ", is an orphan!");
			}		
//		}
			
		calculateFertilityHazard();		//Need to calculate Fertility rate once mortality rates have been loaded in

		//For output tables
		ageBinInterval = (double)Parameters.getMaxAge() / numAgeBinsInTables.doubleValue();		//For output tables, to calculate the size of the age bins
		stats.initialiseStatisticsArrays();			//Set elements in arrays in statistics class to zero
				
	}

	public void buildSchedule() {		
		
		getEngine().getEventList().scheduleOnce(new SingleTargetEvent(this, Processes.ResetTimer), 0., Order.BEFORE_ALL.getOrdering());			//Start timer 
		
		stats.updateStatistics(); 			//Call now before start of simulation so that the statistics exist for inital agent initialisation and process scheduling.
		getEngine().getEventList().scheduleRepeat(new SingleTargetEvent(this, Processes.UpdateStatistics), 1., Order.BEFORE_ALL.getOrdering(), 1.);  //Events repeated every year just before the start of the New Year

		getEngine().getEventList().scheduleOnce(new SingleTargetEvent(this, Processes.Stop), endYear, Order.AFTER_ALL.getOrdering());
		
		long timeToCompleteBuild = System.currentTimeMillis() - elapsedTime;
		log.info("Build completed.  Time taken is " + timeToCompleteBuild + "ms.");
		System.out.println("Build completed.  Time taken is " + timeToCompleteBuild + "ms.");

	}
	
	///////////////////////////////////////////////////////////////////
	// Event Listener
	///////////////////////////////////////////////////////////////////
	
	public enum Processes {
		UpdateStatistics,
		ResetTimer,
		Stop,
	}
	
	public void onEvent(Enum<?> type) {
		switch ((Processes) type) {
		
		case UpdateStatistics:
			stats.updateStatistics();
			break;
		case ResetTimer:
			elapsedTime = System.currentTimeMillis();		//Update elapsedTime.
			break;
		case Stop:
			long timeToComplete = System.currentTimeMillis() - elapsedTime;
			log.info("Model completed.  Time taken to run simulation is " + timeToComplete + "ms.");
			System.out.println("Model completed.  Time taken to run simulation is " + timeToComplete + "ms.");

			if(microsimShellUse) {
//				getEngine().pause();
				getEngine().end();
			}	
			else {
//				getEngine().quit();		//This allows access from the IDE to the database after the simulation is finished. This would also close the GUI if used.
				getEngine().reset();
			}
			
			break;
		}
	}
	
	
	///////////////////////////////////////////////////////////////////////
	// Other methods
	///////////////////////////////////////////////////////////////////////
	
	
	//If any of these conditions are met, the simulation should be aborted, as described in the paper
	private void checkParameters() {
		if(0 > minYearsOfEducation) {
			abortSimulation();
		}
		else if(minYearsOfEducation > maxYearsOfEducation) {
			abortSimulation();
		}
		else if(maxYearsOfEducation > minAgeToReproduce) {
			abortSimulation();
		}
		else if(minAgeToReproduce > maxAgeToReproduce) {
			abortSimulation();
		}
		else if(maxAgeToReproduce > 100) {
			abortSimulation();
		}
		else if (minHealthIndex > maxHealthIndex) {		//Additional condition we specify
			abortSimulation();
		}
		else if (minHealthIndex < 0) {
			abortSimulation();				//Sims cannot have negative healthIndex
		}
	}
	
	private void abortSimulation() {
		log.info("Invalid Model Parameters - Simulation aborted!  Check bounds on parameters for min/max of years in education"
				+ ", healthIndex and age to reproduce.");
		System.out.println("Invalid Model Parameters - Simulation aborted!  Check bounds on parameters for min/max of years in"
				+ " education, healthIndex and age to reproduce.");

		getEngine().reset();			//Prevents simulation from running incorrect parameter configuration
	}

	private void calculateFertilityHazard() {						
		
		//Makes use of calculations already done in obtaining the initialPopAgeDistProbs in Parameters.java.
		double popWeightedAvgDeathRate = Parameters.getMortalityRates()[0];
		double proportionOfFertileInitialPopulation = 0.;
		for(int age = 1; age < Parameters.getInitialPopAgeDistEvents().length; age++) {
			double probSimsReachThisAge = Parameters.getInitialPopAgeDistProbs()[age - 1];
			popWeightedAvgDeathRate += probSimsReachThisAge * Parameters.getMortalityRates()[age];
			if(isFertile((double)age)) {
				proportionOfFertileInitialPopulation += probSimsReachThisAge;
			}
		}
		fertilityHazard = -Math.log( 1 - ( popWeightedAvgDeathRate / proportionOfFertileInitialPopulation ) );

//		System.out.println("fertilityHazard is " + fertilityHazard);
		
	}
	
	//TODO: Should this be replaced by FertileAgeFilter class in data.filter package???????
	public boolean isFertile(Double age) {				//Previously had an Integer age as the argument, but this can now be used in Sim#considerBirth() when age + timeUntilBirth is passed into this method, where timeUntilBirth is a double.
		return ((age >= minAgeToReproduce) && (age < maxAgeToReproduce));
	}
	
	public boolean removeSim(Sim sim) {		
		return sims.remove(sim);
	}

	public Nbhd getNbhd(Integer nbhdId) {			
		return nbhds.get(nbhdId);
	}

	

	///////////////////////////////////////////////////////////////
	// Access methods
	///////////////////////////////////////////////////////////////


	public Set<Sim> getSims() {
		return sims;
	}
	public List<Nbhd> getNbhds() {
		return nbhds;
	}
	public Integer getEndYear() {
		return endYear;
	}
	public Integer getMinAgeToReproduce() {
		return minAgeToReproduce;
	}
	public Integer getMaxAgeToReproduce() {
		return maxAgeToReproduce;
	}
	public Integer getAgeBand() {
		return ageBand;
	}
	public Double getMaxOccupancyFactor() {
		return maxOccupancyFactor;
	}
	public Integer getMinYearsOfEducation() {
		return minYearsOfEducation;
	}
	public Integer getMaxYearsOfEducation() {
		return maxYearsOfEducation;
	}
	public Double getMinHealthIndex() {
		return minHealthIndex;
	}
	public Double getMaxHealthIndex() {
		return maxHealthIndex;
	}
	public Double getMinBaseIncome() {
		return minBaseIncome;
	}
	public Double getMaxBaseIncome() {
		return maxBaseIncome;
	}
	public double getFertilityHazard() {
		return fertilityHazard;
	}

	public Statistics getStats() {
		return stats;
	}

	public CityType getCityType() {
		return cityType;
	}

	public IncomeBaseGiniCoefficient getIncomeBaseGiniCoeff() {
		return incomeBaseGiniCoeff;
	}

	public Integer getStartingPopulationSize() {
		return startingPopulationSize;
	}

	public Boolean getFixRandomSeed() {
		return fixRandomSeed;
	}

	public Integer getRandomSeedIfFixed() {
		return randomSeedIfFixed;
	}

	public void setCityType(CityType cityType) {
		this.cityType = cityType;
	}

	public void setIncomeBaseGiniCoeff(IncomeBaseGiniCoefficient incomeBaseGiniCoeff) {
		this.incomeBaseGiniCoeff = incomeBaseGiniCoeff;
	}

	public void setEndYear(Integer endYear) {
		this.endYear = endYear;
	}

//	public Boolean getLoadInitialPopulationFromDatabase() {
//		return loadInitialPopulationFromDatabase;
//	}
//	
//	public void setLoadInitialPopulationFromDatabase(
//			Boolean loadInitialPopulationFromDatabase) {
//		this.loadInitialPopulationFromDatabase = loadInitialPopulationFromDatabase;
//	}

	public void setStartingPopulationSize(Integer startingPopulationSize) {
		this.startingPopulationSize = startingPopulationSize;
	}

	public void setMinAgeToReproduce(Integer minAgeToReproduce) {
		this.minAgeToReproduce = minAgeToReproduce;
	}

	public void setMaxAgeToReproduce(Integer maxAgeToReproduce) {
		this.maxAgeToReproduce = maxAgeToReproduce;
	}

	public void setMinYearsOfEducation(Integer minYearsOfEducation) {
		this.minYearsOfEducation = minYearsOfEducation;
	}

	public void setMaxYearsOfEducation(Integer maxYearsOfEducation) {
		this.maxYearsOfEducation = maxYearsOfEducation;
	}

	public void setMinBaseIncome(Double minBaseIncome) {
		this.minBaseIncome = minBaseIncome;
	}

	public void setMaxBaseIncome(Double maxBaseIncome) {
		this.maxBaseIncome = maxBaseIncome;
	}

	public void setAgeBand(Integer ageBand) {
		this.ageBand = ageBand;
	}

	public void setMaxOccupancyFactor(Double maxOccupancyFactor) {
		this.maxOccupancyFactor = maxOccupancyFactor;
	}

	public void setFixRandomSeed(Boolean fixRandomSeed) {
		this.fixRandomSeed = fixRandomSeed;
	}

	public void setRandomSeedIfFixed(Integer randomSeedIfFixed) {
		this.randomSeedIfFixed = randomSeedIfFixed;
	}

	public void setMinHealthIndex(Double minHealthIndex) {
		this.minHealthIndex = minHealthIndex;
	}

	public void setMaxHealthIndex(Double maxHealthIndex) {
		this.maxHealthIndex = maxHealthIndex;
	}

	public long getElapsedTime() {
		return elapsedTime;
	}

	public static Logger getLog() {
		return log;
	}

	public Integer getRecordDataAfterYear() {
		return recordDataAfterYear;
	}

	public void setRecordDataAfterYear(Integer recordDataAfterYear) {
		this.recordDataAfterYear = recordDataAfterYear;
	}

	public Integer getNumAgeBinsInTables() {
		return numAgeBinsInTables;
	}

	public void setNumAgeBinsInTables(Integer numAgeBinsInTables) {
		this.numAgeBinsInTables = numAgeBinsInTables;
	}

	public double getAgeBinInterval() {
		return ageBinInterval;
	}
	
	public void setNumYearsInTableBins(double ageBinInterval) {
		this.ageBinInterval = ageBinInterval;
	}

	public boolean isMicrosimShellUse() {
		return microsimShellUse;
	}

	public void setMicrosimShellUse(boolean microsimShellUse) {
		this.microsimShellUse = microsimShellUse;
	}

}	