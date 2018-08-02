package jasmine.thim.model;

import microsim.data.db.PanelEntityKey;
import microsim.engine.SimulationEngine;
import microsim.event.Event;
import microsim.event.EventListener;
import microsim.event.Order;
import microsim.event.SingleTargetEvent;
import microsim.statistics.IDoubleSource;
import microsim.statistics.IIntSource;
import microsim.statistics.regression.RegressionUtils;
import jasmine.thim.data.Parameters;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.apache.commons.math.ArgumentOutsideDomainException;


@Entity
public class Sim implements EventListener, IDoubleSource, IIntSource {
	
	public static long simIdCounter = 0;
	
	@Transient
	private THIMModel model;
	
	@Id
	private PanelEntityKey key;
	
	private int age;		
	
	@Transient
	private double birthTimestamp;				//The day of the year (0 to 364) that the Sim was born on.
	
	@Column(name="years_in_education")
	private int yearsInEducation;		//Number of years the sim spends in education
	
	@Transient
	private double incomeBase;		//A unit-less positive real number, acts as a factor to derive 
									//income	
	private double income;		//Income ($)
	
	@Column(name="health_index")
	private double healthIndex;		//Health index initiated with perfect health
	
	@Column(name="cumulative_lifetime_earnings")
	private double cumulativeIncome;
	
	@Column(name="cumulative_health_index")
	private double cumulativeHealthIndex;
	
	@Transient
	private Nbhd nbhd;
	
	@Column(name="neighbourhood_id")
	private int nbhdId;			//Necessary to have in addition to a pointer to the nbhd, so that the sim's nbhd can be identified in the database 
		
	@Transient
	private Sim parent;

//	@Column(name="parent_id")
//	private long parentId;			//Only need, if wanting to persist the identity of the parent to the database  

	@Transient
	private List<Sim> childSims; 

	@Transient
	private boolean isDead;
	
	//To allow unscheduling of sim's repeated events when the sim dies 
	@Transient
	private Event simYearlyEvents;
	
//	@Transient
//	private Event simConsiderBirth;

//	@Transient
//	private Event simGiveBirth;
		
//	@Transient
//	private Event simStopFollowingParent;

	/////////////////////////////////////////////////////////////
	// Event listener implementation
	/////////////////////////////////////////////////////////////


	public enum Processes {

		StopFollowingParent,
		ConsiderBirth,					
		GiveBirth,
		Death, 
		YearlyEvents,
	}

//	@Override
	public void onEvent(Enum<?> type) {
		switch ((Processes) type) {
		
		case YearlyEvents:
			ageing();
			updateIncome();
			updateHealth();
			considerDeath();	
			considerLocation();
			break;

		case StopFollowingParent:
			stopFollowingParent();
			break;
		case ConsiderBirth:
//			System.out.println("ConsiderBirth, sim id " + this.id.getId());
			considerBirth();
			break;
		case GiveBirth:
//			System.out.println("Population size is " + model.getSims().size() + " GiveBirth" + " Id " + this.getId().getId());
			giveBirth();
			break;
		case Death:
//			System.out.println("Population size is " + model.getSims().size() + " Death for Sim Id " + this.getId().getId());
			death();
			break;
			
		}
	}
		

	///////////////////////////////////////////////////////
	// IDoubleSource and IIntSource implementations
	///////////////////////////////////////////////////////

	
	public enum Variables {
	
		age,
		income,
		healthIndex,
		yearsInEducation,
		
	}
	
	public double getDoubleValue(Enum<?> variableID) {

		switch ((Variables) variableID) {
		
		case income:
			return income;
		case healthIndex:
			return healthIndex;

		default:
			throw new IllegalArgumentException("Unsupported variable " + variableID.name() + " in Sim#getDoubleValue");
		}
		
	}

	public int getIntValue(Enum<?> variableID) {
		
		switch ((Variables) variableID) {

		case age:
			return age;
		case yearsInEducation:
			return yearsInEducation;

		default:
			throw new IllegalArgumentException("Unsupported variable " + variableID.name() + " in Sim#getIntValue");
		}
	}
	
		
	////////////////////////////////////////////////////////////
	// Constructors
	////////////////////////////////////////////////////////////
	

	//Default constructor called at start of simulation
	public Sim() {
		super();
	}
	
	//Constructor used for creating initial population
	public Sim(long idNumber) {
		this();
		key = new PanelEntityKey();
		key.setId(simIdCounter);
		
		income = 0.;		//This will be updated when the Sim's age is greater than yearsInEducation
		cumulativeIncome = 0.;
		
		childSims = new LinkedList<Sim>();
		
		isDead = false;
		
		model
		= (THIMModel) SimulationEngine.getInstance().getManager(THIMModel.class.getCanonicalName());

		simYearlyEvents = new SingleTargetEvent(this, Processes.YearlyEvents);
//			simConsiderBirth = new SingleTargetEvent(this, Processes.ConsiderBirth);
//			simGiveBirth = new SingleTargetEvent(this, Processes.GiveBirth);
//			simStopFollowingParent = new SingleTargetEvent(this, Processes.StopFollowingParent);			


	}

	//Constructor called when a Sim gives birth to create newborn Sim.
	public Sim( Sim parent ) {
		this((Sim.simIdCounter)++);
		
		this.parent = parent;
		
		age = 0;			//Newborn
		double currentTime = SimulationEngine.getInstance().getTime();
		birthTimestamp = currentTime - (long)currentTime;		//Don't actually need it to run the simulation		
				
		healthIndex = model.getMaxHealthIndex();	//Newborns have maximum healthIndex (= 1 in THIM paper)
		cumulativeHealthIndex = healthIndex;
		
		//Use information from parent to establish education, incomeBase and nbhd
		//Set yearsInEducation here
		yearsInEducation = calculateYearsInEducation(parent);
		
		//Set incomeBase here
		incomeBase = calculateIncomeBase(yearsInEducation, parent);			//Needs yearsInEducation to have already been calculated (i.e. call calculateYearsInEducation(parent) first!)

		//Set new sim's nbhd to that of the parent.
		this.nbhd = parent.getNbhd();					
		this.setNbhdId((int) nbhd.getKey().getId());

		scheduleNewBornSimEvents();				//Schedule future events where the date is known at birth (e.g. when the Sim becomes fertile and calls considerBirth for the first time, when the sim finishes education etc. 

	}
	
	
	////////////////////////////////////////////////////////////
	// Scheduling methods
	////////////////////////////////////////////////////////////	

	public void scheduleInitialSimEvents() {		//For initial sim population

		model.getEngine().getEventQueue().scheduleRepeat(simYearlyEvents, birthTimestamp, -1, 1.);			//Events that are repeated every year
		
		long yearsToFinishEducation = Math.max(1, (yearsInEducation-age)) - 1;    //Has the value of 0 if the same age or already older than yearsInEducation, i.e. have already finished education, but also if less than a year to go until finishing education (e.g. the sim is 14 years old and has 15 years of education - as we are at the start of the new year, the sim will turn 15 during the forthcoming year).
//		double timeSimFirstEarnsIncome = SimulationEngine.getInstance().getTime() + birthTimestamp + yearsToFinishEducation;		//On the Sim's birthday, either on the day they finish education, or the first birthday since the start of the simulation if they have already finished education when the simulation starts.
		double timeSimFirstEarnsIncome = birthTimestamp + (double)yearsToFinishEducation;		//On the Sim's birthday, either on the day they finish education, or the first birthday since the start of the simulation if they have already finished education when the simulation starts.
		model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.StopFollowingParent), timeSimFirstEarnsIncome, Order.BEFORE_ALL.getOrdering());						


		if(age < model.getMaxAgeToReproduce()) {
			//Set to consider calculating waiting time to give birth when the Sim's age reaches the minimum age to reproduce.
//			double timeSimFirstConsidersBirth = SimulationEngine.getInstance().getTime() + birthTimestamp + Math.max(1, model.getMinAgeToReproduce()-age) - 1;
			double timeSimFirstConsidersBirth = birthTimestamp + (double)(Math.max(1, model.getMinAgeToReproduce()-age) - 1);
			model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.ConsiderBirth), timeSimFirstConsidersBirth, 1);		//Again, max(1,) used so that if Sim has age equal to or greater than minAgeToReproduce, considerBirth will be scheduled to occur sometime in the forthcoming year depending on the value of the birthDayOffset.  Note that considerBirth is only rescheduled within the birth() method, i.e. only if/when a Sim gives birth to its first child sim, will it schedule another considerBirth.
		}		
	}
	
	public void scheduleNewBornSimEvents() {		//For newborn sims  
		model.getEngine().getEventQueue().scheduleRepeat(simYearlyEvents, SimulationEngine.getInstance().getTime() + 1., -1, 1.);			//Events that are repeated every year
		model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.StopFollowingParent), SimulationEngine.getInstance().getTime() + (double)yearsInEducation, Order.BEFORE_ALL.getOrdering());
		double timeSimFirstConsidersBirth = SimulationEngine.getInstance().getTime() + (double)model.getMinAgeToReproduce();
		model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.ConsiderBirth), timeSimFirstConsidersBirth, 1);		//Note that considerBirth is only rescheduled within the birth() method, i.e. only if/when a Sim gives birth to its first child sim, will it schedule another considerBirth.
		
	}

	
	////////////////////////////////////////////////////////////
	// Initialization methods (for the initial population only)
	////////////////////////////////////////////////////////////
	
	void configureInitialSimPropertiesAndSchedule() {			//Called at start of simulation by buildObjects in model class
		//Set neighbourhood, age, healthIndex, yearsInEducation and incomeBase here.  
		//Similar but not necessarily exactly the same implementation as in ModGen.  
		//Set by default or sampled from a distribution (and there is no info from parent available 
		//here.
		drawAge();				//Returns a random number representing the birthday (if 0, it corresponds to January 1st).
		drawInitialHealthIndex();
		drawYearsInEducation();
		drawIncomeBase();			//Requires yearsInEducation to be set previously, as addSim() needs to know whether to add Sim to adultResidents and workerResidents lists
		drawInitialNbhdId();		//Requires age to be set previously
		drawIncome();
		
		scheduleInitialSimEvents();
		 
	}

	private void drawInitialNbhdId() {	//This is different to ModGen implementation, which seems to randomly distribution nbhds for adults, then assign children to the nbhd of their parents.  Our method ensures even distribution across all nbhds at start of simulation.			
		nbhdId = ((int)(key.getId() % Parameters.getSimulatedNeighborhoods()));
		nbhd = model.getNbhd(nbhdId);
	}

	private void drawAge() {
		//  A random draw from the distribution of ages produced from the input mortality rates, with the assumption of a stable population size  
		age = RegressionUtils.event(Parameters.getInitialPopAgeDistEvents(), Parameters.getInitialPopAgeDistProbs(), SimulationEngine.getRnd());
		birthTimestamp = SimulationEngine.getRnd().nextDouble();
	}

	private void drawInitialHealthIndex() {
		healthIndex = model.getMaxHealthIndex();			// Can control via the GUI, the initial healthIndex.  Note, ModGen version has initial population sims starting with healthIndex = 1.  "Some day -- develop a better distribution" (taken from ModGen code).
		cumulativeHealthIndex = age * (model.getMaxHealthIndex() + healthIndex) / 2.;			//Take mid-point between maximum health index (assumed at birth) and health index on initialization.  Currently, health index is initialised to max health index, but in case this is changed in future, this equation will still be valid as a mid-point proxy.
	}
	
	private void drawYearsInEducation() {
		// As implemented in ModGen version of model, yearsInEducation is drawn from a uniform distribution bounded by min/maxYearsOfEducation model parameters.
		yearsInEducation = model.getMinYearsOfEducation() + SimulationEngine.getRnd().nextInt(1+ model.getMaxYearsOfEducation() - model.getMinYearsOfEducation());
	}

	private void drawIncomeBase() {			//Requires yearsInEducation to have been set prior to calling this method
		double incomeBaseRand = RegressionUtils.eventPiecewiseConstant(Parameters.getIncomeBaseDistEvents(), Parameters.getIncomeBaseDistProbs(), SimulationEngine.getRnd());
		incomeBase = Math.exp(incomeBaseRand);
		if(incomeBase > model.getMaxBaseIncome()) {
			incomeBase = model.getMaxBaseIncome();
		}
		else if (incomeBase < model.getMinBaseIncome()) {
			incomeBase = model.getMinBaseIncome();
		}
	}
	
	private void drawIncome() {
		if(age >= yearsInEducation) {
			income = incomeBase * 
					Parameters.getAgeIncomeProfile()[age] * 
//					Math.exp(Parameters.getStandardNormal().nextDouble() * Math.sqrt(Parameters.getYSigma()));
					Math.exp(Parameters.getStandardNormal().nextDouble() * Parameters.getYSigma());
			if(income <= 0) {
				throw new RuntimeException("Income is not positive!");
			}
			
			for(int ageBeforeStartOfSimulation = yearsInEducation; ageBeforeStartOfSimulation <= age; ageBeforeStartOfSimulation++) {
				cumulativeIncome = Parameters.getAgeIncomeProfile()[ageBeforeStartOfSimulation];			//Assume no noise in process (just use average income for age, adjusted for income base below) for the Sims' earning years before the simulation starts
			}
			cumulativeIncome *= incomeBase;			//No need to multiply incomeBase factor within the loop above as can do so here.
		}
	}	


	
	//////////////////////////////////////////////////////////////
	// Newborn Sim field initializations
	//////////////////////////////////////////////////////////////
	
	private int calculateYearsInEducation(Sim parent) {
		double parentIncomeRelativeToCityAverage = 1.;		//Set initially as if it equals the average (see below the comment for the if condition)
		double nbhdAdultIncomeRelativeToCityAverage = 1.;		//Set initially as if it equals the average (see below the comment for the if condition)
		double cityAvgAdultIncome = model.getStats().getAvgAdultIncome();
		if(cityAvgAdultIncome > 0.) {			//Unlikely for large Sim populations, but it's possible that all Sims were still in Education in the last calendar year.  The parent then reaches the MinAgeToReproduce (which = 20 = MaxYearsInEducation in this case), and has a child within the same calendar year, as their timeUntilBirth < 1 year.  In this case, parent and nbhd incomes assumed to equal city avg, so that they have no effect on equation (log(1) = 0).
			parentIncomeRelativeToCityAverage = parent.getIncome() / cityAvgAdultIncome;
			nbhdAdultIncomeRelativeToCityAverage = parent.getNbhd().getNbhdAdultIncomeRelativeToCityAvg();
		}
  
		int years = (int)(Parameters.getEMean() + 
				(Parameters.getEBetaIncPar() * Math.log(parentIncomeRelativeToCityAverage)) +
				(nbhdAdultIncomeRelativeToCityAverage <= 0.? 0. : Parameters.getEBetaIncNbhd() * Math.log(nbhdAdultIncomeRelativeToCityAverage) ) +		//It's possible for Sim to reach minAgeToReproduce and have a baby in the same calendar year, whilst also starting work but the nbhd average income hasn't yet been updated, so is 0.  In this case, nbhdIncomeRelativeToCityAverage = 0, and log(0) -> -Infinity, so in this case we ignore this factor.
//				Parameters.getStandardNormal().nextDouble() * Math.sqrt(Parameters.getESigma()));			//Incorrectly specified in THIM paper compared to usage in ModGen version.  Paper defines Normal distribution as N(0, ESigma) whereas common usage is N(mean, variance).  Also incorrect definition of ESigma in ModGen - has comment "//EN Variance of education" but uses it as standard deviation in equations.
				(Parameters.getStandardNormal().nextDouble() * Parameters.getESigma()));
				
		if(years > model.getMaxYearsOfEducation()) {					//Truncate to ensure yearsInEducation is within the bounds [MinYearsOfEducation, MaxYearsOfEduction]
			return model.getMaxYearsOfEducation();
		}
		else if (years < model.getMinYearsOfEducation()) {
			return model.getMinYearsOfEducation();
		}
		else return years;
	}

	private double calculateIncomeBase(int yearsInEducation, Sim parent) {

		double incomeBaseRand = Math.exp(RegressionUtils.eventPiecewiseConstant(Parameters.getIncomeBaseDistEvents(), Parameters.getIncomeBaseDistProbs(), SimulationEngine.getRnd()));		
		double parentIncomeRelativeToCityAverage = 1.;		//Set initially as if it equals the average (see below the comment for the if condition)
		double nbhdIncomeRelativeToCityAverage = 1.;		//Set initially as if it equals the average (see below the comment for the if condition)
		double cityAvgAdultIncome = model.getStats().getAvgAdultIncome();
		if(cityAvgAdultIncome > 0) {						//Unlikely for large Sim populations, but it's possible that all Sims were still in Education in the last calendar year.  The parent then reaches the MinAgeToReproduce (which = 20 = MaxYearsInEducation in this case), and has a child within the same calendar year, as their timeUntilBirth < 1 year.  In this case, parent and nbhd incomes assumed to equal city avg, so that they have no effect on equation (log(1) = 0).
			parentIncomeRelativeToCityAverage = parent.getIncome() / cityAvgAdultIncome;
			nbhdIncomeRelativeToCityAverage = parent.getNbhd().getNbhdAdultIncomeRelativeToCityAvg();
		}
		
		double cityAvgEd = Parameters.getEMean();	//model.getAvgCityEducation();
		
		double yBase = incomeBaseRand +		 
				(cityAvgEd <= 0.? 0. : Parameters.getYBetaEduc() * Math.log(((double)yearsInEducation) / cityAvgEd)) +
				(Parameters.getYBetaIncPar() * Math.log(parentIncomeRelativeToCityAverage)) +
				(nbhdIncomeRelativeToCityAverage <= 0.? 0. : Parameters.getYBetaIncNbhd() * Math.log(nbhdIncomeRelativeToCityAverage));		//Only add this if AvgNbhdIncome > 0.  If AvgNbhdIncome = 0, this is likely to be because the parent of this Sim has just moved to a Nbhd where it is the only Sim earning an income (when Sims are sparsely distributed across nbhds).  Therefore, there should be no effect from AvgNbhdIncome in this calculation of YBase.
		
		if(yBase > model.getMaxBaseIncome()) {
			return model.getMaxBaseIncome();
		}
		else if(yBase < model.getMinBaseIncome()) {
			return model.getMinBaseIncome();
		}
		else return yBase;

	}
	
	
	//////////////////////////////////////////////////////////
	// Processes
	//////////////////////////////////////////////////////////
	
		
	protected void ageing() {
			age++;
	}
	
	protected void stopFollowingParent() {		//Break parent-child link

		if((parent != null)) {					//There is no need to check for age as this is scheduled when a Sim is born.
			parent.getChildSims().remove(this);
			parent = null;				//Need this, as null pointer if parentId not set to null as if parent dies first, then this Sim dies, in death() there is a call to access the parent to remove this from the childSims list.
		}

	}

	protected void updateIncome() { 
		if(age >= yearsInEducation) {
			double avgIncomeForAge = Parameters.getAgeIncomeProfile()[age];
			income = incomeBase * avgIncomeForAge * 
//					Math.exp(Parameters.getStandardNormal().nextDouble() * Math.sqrt(Parameters.getYSigma()));		//Incorrectly specified in THIM paper compared to usage in ModGen version.  Paper defines Normal distribution as N(0, YSigma) whereas common usage is N(mean, variance).  YSigma in ModGen has comment "//EN Standard deviation of perturbation term in annual income change equation" and uses it as such in equations.
					Math.exp(Parameters.getStandardNormal().nextDouble() * Parameters.getYSigma());
			if(income <= 0) {
				throw new RuntimeException("Income is not positive!");
			}
			cumulativeIncome += income;
		}
	}

	protected void updateHealth() {

//		double hDeltaRand = (RegressionUtils.event(Parameters.getHealthDeltaEvents(), Parameters.getHealthDeltaProbs(), SimulationEngine.getRnd())).doubleValue();		//Assuming a discrete probability mass histogram
		double hDeltaRand = RegressionUtils.eventPiecewiseConstant(Parameters.getHealthDeltaEvents(), Parameters.getHealthDeltaProbs(), SimulationEngine.getRnd());			//Assuming a piecewise constant probability density, where the domain is continuous (compact?)
		double hDeltaIncome = 0;
		if((income > 0) && (age >= (model.getMinAgeToReproduce() - model.getAgeBand()))) {		//avgIncomeNearAge not specified for ages below MinAgeToReproduce - AgeBand.
			double avgAdultIncomeNearAge = model.getStats().getAvgAdultIncomeNearAge(age);				

			if(avgAdultIncomeNearAge > 0.) {			//Could be zero if no-one is earning because they are still in education, though this is unlikely to be true for all Sims, if there is a sizeable Sim population 
				hDeltaIncome = Parameters.getHIncParm() * Math.log(income / avgAdultIncomeNearAge);
			}   
		}

		healthIndex += ( hDeltaIncome + hDeltaRand ) * 
				(age / (double)Parameters.getMaxAge());
		if(healthIndex > model.getMaxHealthIndex()) {								//Check healthIndex remains within bounds (paper has min = 0, max = 1).
			healthIndex = model.getMaxHealthIndex();
		}
		else if(healthIndex < model.getMinHealthIndex()) {
			healthIndex = model.getMinHealthIndex();
		}
		cumulativeHealthIndex += healthIndex;

	}

	protected void considerLocation() {
 
		if(age >= yearsInEducation) {

			double incomeDifference = 0.;
			double avgNbhdInc = nbhd.getAvgNbhdAdultIncome();
			if(avgNbhdInc > 0) {			//Could be the case that all Sims in a neighbourhood are not receiving an income as they have ages < their yearsInEducation (except for this Sim, who has just reached the age to receive)
				incomeDifference = Math.abs(income - avgNbhdInc) / avgNbhdInc;	//Note, income has been updated in schedule AFTER avgNbhdInc has been calculated.  So, it is possible for a nbhd with only 1 Sim to have a non-zero incomeDifference, as the avgNbhdInc was calculated at the start of the day.  This is so that all Sims have the same avg statistics to use when making their decisions, so the ordering of the Sims who share the same birthday (and hence update their income and consider their location at the same time-step) does not matter. 
			} 
				
			double propensityToMove = 0.;
			
			try {
				propensityToMove = Parameters.getPropensityToMoveFunction().value(incomeDifference);
			} catch (ArgumentOutsideDomainException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			if ( RegressionUtils.event(propensityToMove) ) {		//Where propensityToMove is the probability of success of a Bernoulli random variable
				// Attempt move (if there is space and a nbhd with less income discrepancy
				
				List<Nbhd> potentialNbhds = new ArrayList<Nbhd>();			//Potential neighbourhoods that have space for the Sim to move to
				
				for(Nbhd otherNbhd : model.getNbhds()) {
					if(!otherNbhd.equals(nbhd)) {			//Note that the condition below is measured at the start of the year, before Sims have moved.  The current Sim does not take into account whether other Sims have already moved earlier in the year (this removes bias in the ordering of Sims' birthTimestamps, though it may mean in borderline cases that the sim will move into a nbhd which has reached maximum occupancy earlier in the year).
						if(otherNbhd.getNumWorkingResidents() < (model.getMaxOccupancyFactor() * (double)model.getStats().getNumWorkingSims() / (double)Parameters.getSimulatedNeighborhoods())) { 
							potentialNbhds.add(otherNbhd);		//TODO: The criterion above appears in the ModGen code as 'MaxAdultsInNeighborhood = (int) ( MaxOccupancyFactor * StationaryAdultPopulationSize / SimulatedNeighborhoods );'.  Check if this is updated in the code, otherwise the implementation in our code is different, as it uses the latest number of Sims who have finished their education (getSimsAfterEducation(), which represents the number of adults).
						}						
					}		
				}
				
				double bestIncomeDifference = incomeDifference;
				Nbhd bestNbhd = nbhd;							//Initialize to current nbhd
				for(Nbhd tryNbhd : potentialNbhds) {
					double avgPotentialNbhdIncome = tryNbhd.getAvgNbhdAdultIncome();
					double potentialIncomeDiff = bestIncomeDifference;
					if(avgPotentialNbhdIncome > 0.) {
						potentialIncomeDiff = Math.abs(income - avgPotentialNbhdIncome) / avgPotentialNbhdIncome;		//Positive and negative differences treated equally
					}
					if(potentialIncomeDiff < bestIncomeDifference) {			
						bestIncomeDifference = potentialIncomeDiff;
						bestNbhd = tryNbhd;
					}
				}
				if(bestNbhd != nbhd) {
					moveNbhd(bestNbhd);					//If no other nbhd had a smaller income discrepancy, then nbhdId will remain the same (don't move)
					for(Sim child : childSims) {		//When Sim moves, need their children (if they are a parent) to follow to new nbhd.  When child reaches age where they finish education and start earning an income, they remove themselves from the parent's childSim list so that they no longer follow the parent around.
						child.moveNbhd(bestNbhd);
					}					
				}
			}
		}
	}
	
	private void moveNbhd(Nbhd newNbhd) {
		nbhd = newNbhd;
		nbhdId = (int) nbhd.getKey().getId();		
	}

	protected void considerBirth() {   
		
		if(!isDead) {					//If Sim has already died, no need to add any future events
			//Calculate time until giving birth, timeUntilBirth 
			double timeUntilBirth = -Math.log( SimulationEngine.getRnd().nextDouble() ) / model.getFertilityHazard();

			//Schedule birth event if Sim will have age less than maxAgeToReproduce when the birth is due to occur 
			if(age + (int)timeUntilBirth < model.getMaxAgeToReproduce()) {
				model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.GiveBirth), SimulationEngine.getInstance().getTime() + timeUntilBirth, 10);		//Note that considerBirth is only rescheduled within the birth() method, i.e. only if/when a Sim gives birth to its first child sim, will it schedule another considerBirth.
			}			
		}
	}
	
	protected void giveBirth() {

		if(!isDead) {					//If Sim has already died, cannot give birth, nor re-call considerBirth()
			Sim newborn = new Sim(this);
			double currentTime = SimulationEngine.getInstance().getTime();
			newborn.setBirthTimestamp(currentTime - (long)currentTime);
			if(!model.getSims().add(newborn)) {
				throw new RuntimeException("Model failed to add newborn sim " + newborn.getKey().getId() + " to the set of sims");
			}
			if(!childSims.add(newborn)) {			//Add child Sim to parent's list of childSims, so that children can be informed of moving nbhd when parent moves.
				throw new RuntimeException("Model failed to add newborn sim " + newborn.getKey().getId() + " to the set of child sims of parent sim " + key.getId());
			}
			considerBirth();			//Now consider possibility of giving birth to next child in the future and schedule as necessary, as long as the Sim's age is less than the maximum age to reproduce.
		}
	}
	
	protected void considerDeath() {	//Yearly Event
		double timeUntilDeath = 0.;
		if(healthIndex > 0) {			//When healthIhdex = 0, timeUntilDeath = 0

			double mortalityHazard = Parameters.getAvgMortalityHazards()[age];
			if(!Double.isInfinite(mortalityHazard)) {			//Infinite mortalityHazard when mortalityRate = 1 (the case for Sims aged 100).  The infinity messes up the arithmetic below, so must handle this case separately (by keeping timeUntilDeath = 0, so the Sim with infinite mortalityHazard is immediately removed from the simulation).
				double incomeEffect = 1.;
				//If income is zero, ignore this component of the mortality hazard, as Sims who have ages below their yearsInEducation have no income yet.  However, they should not have an increased mortalityHazard due to their youth!
				if((income > 0) && (age >= (model.getMinAgeToReproduce() - model.getAgeBand()))) {			//The second condition is necessary, as if Sim has e.g. yearsInEducation = 1, they will have income > 0 when they are 1 year old and above, however avgIncomeNearAge is only defined for ages >= minAgeToReproduce - ageBand.  So if minAgeToReproduce = 20 and ageBand = 10, avgIncomeNearAge will be undefined for ages less than 10, so this income effect should not be applied for sims aged less than 10. 
					incomeEffect = Math.pow( model.getStats().getAvgAdultIncomeNearAge(age) / income, Parameters.getMBetaIncNear());
				}
				mortalityHazard *= incomeEffect *
						Math.pow( model.getStats().getAvgHealth() / healthIndex , Parameters.getMBetaH());			//If healthIndex reaches zero, mortalityHazard should tend to a positive large number, so that timeToDeath tends to 0. TODO: Careful - check when avgCityHealth is calculated.  It should have a value accurate just before this Sim's birthday...

				if(mortalityHazard > 0.) {
					timeUntilDeath = -Math.log( SimulationEngine.getRnd().nextDouble() ) / mortalityHazard;	
				}
				else if(mortalityHazard == 0.) {		//For newborn Sims, the intput parameters from the paper specify mortality rate = 0, which leads to a mortalityHazard = 0, which would lead to timeUntilDeath = Infinity, which is not well specified.  
					timeUntilDeath = Double.MAX_VALUE;		//Designed so that the Sims with mortalityHazard = 0 do not die (until they are scheduled to considerDeath again at an age where their mortalityHazard (and mortality rate) is no longer 0.   
				}
				else if(mortalityHazard < 0.) {
					throw new IllegalArgumentException("mortalityHazard cannot be negative.  Check the mortality_rate.xls inputs and ensure that all values are between 0 and 1 (inclusive)!");
				}
			}
		}
		if(timeUntilDeath < 1.) {
			model.getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.Death), SimulationEngine.getInstance().getTime() + timeUntilDeath, 9);
		}		
	}
	
	protected void death() {
		model.getEngine().getEventQueue().unschedule(simYearlyEvents);		//Remove yearly events of this sim from the schedule
		
		stopFollowingParent();		//If still following parent, Sim removes itself from parent's childSims list so that parent does make this Sim move nbhd in future, as this Sim is about to die.
		for(Sim child : childSims) {		//(If they have childSims)
			child.setParent(null);		//Break link between child and parent, so that child doesn't need to remove themselves from the childSim list when they finish their education (in the ageing() method)
		}
		
		//For output data tables (not necessary for model evolution)
		double currentTimeInYear = SimulationEngine.getInstance().getTime() - (long)SimulationEngine.getInstance().getTime();
		double fractionOfYearSinceLastBirthday = currentTimeInYear - birthTimestamp; 
		if(fractionOfYearSinceLastBirthday < 0) {
			fractionOfYearSinceLastBirthday  += 1.;		//birth'day' (birthTimestamp + current year) has not happened in this calendar year, so need to increment by 1 to represent the time since the birth'day' last year 
		}
		double ageAtDeath = (double)age + fractionOfYearSinceLastBirthday;
		cumulativeIncome -= income * (1. - fractionOfYearSinceLastBirthday);				//Deduct income not received as Sim has died before the full birth year has been completed
		cumulativeHealthIndex -= healthIndex * (1. - fractionOfYearSinceLastBirthday);		//Reduce healthIndex as Sim has died before the full birth year has been completed
		model.getStats().recordStatisticsAtDeath(cumulativeHealthIndex, cumulativeIncome, ageAtDeath);		
		
		isDead = true;							//This prevents methods like considerBirth and giveBirth having any effect on population grow after the sim has died.
		if(!model.removeSim(this)) {			//Sets the sim reference to null
			throw new RuntimeException("Sim " + key.getId() + " not removed from either THIMModel.sims!");
		}

	}

	
	
	//////////////////////////////////////////////////////
	// Access methods
	//////////////////////////////////////////////////////
	


	public PanelEntityKey getKey() {
		return key;
	}
	public int getYearsInEducation() {
		return yearsInEducation;
	}
	public double getIncome() {
		return income;
	}
	public int getAge() {
		return age;
	}
	public Sim getParent() {
		return parent;
	}
	public void setParent(Sim parent) {
		this.parent = parent;
	}
	public List<Sim> getChildSims() {
		return childSims;
	}
	public THIMModel getModel() {			//Used in filter classes
		return model;
	}
	public Nbhd getNbhd() {
		return nbhd;
	}
	public double getBirthTimestamp() {
		return birthTimestamp;
	}
	public void setBirthTimestamp(double birthTimestamp) {
		this.birthTimestamp = birthTimestamp;
	}
	public boolean isDead() {		//TODO: Can we remove this?
		return isDead;
	}

	public int getNbhdId() {
		return nbhdId;
	}

	public void setNbhdId(int nbhdId) {
		this.nbhdId = nbhdId;
	}

	public double getHealthIndex() {
		return healthIndex;
	}

}
