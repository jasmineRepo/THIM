package jasmine.thim.experiment;

import microsim.annotation.ModelParameter;
import microsim.engine.AbstractSimulationObserverManager;
import microsim.engine.SimulationCollectorManager;
import microsim.engine.SimulationManager;
import microsim.event.CommonEventType;
import microsim.event.EventGroup;
import microsim.event.Order;
import microsim.gui.GuiUtils;
import microsim.gui.plot.TimeSeriesSimulationPlotter;
import microsim.statistics.CrossSection;
import microsim.statistics.IIntSource;
import microsim.statistics.functions.MeanArrayFunction;
import microsim.statistics.functions.MultiTraceFunction;
import jasmine.thim.model.Sim;
import jasmine.thim.model.THIMModel;

import org.apache.log4j.Logger;

public class THIMObserver extends AbstractSimulationObserverManager implements IIntSource {

	final THIMCollector collector = (THIMCollector) getCollectorManager();
	
	private final static Logger log = Logger.getLogger(THIMObserver.class);

	@ModelParameter(description="Toggle to turn off Observer for increased execution speed")
	private Boolean observerOn = true;				//Observer contains methods to plot to GUI and persist extra data to the Statistics database that is not actually required to run the simultation processes, e.g. city-wide average years in education using the fMeanEducation field below. 
	
	@ModelParameter
	private Double displayFrequency = 1.;

	private TimeSeriesSimulationPlotter agePlotter;
	private TimeSeriesSimulationPlotter healthPlotter;
	private TimeSeriesSimulationPlotter incomePlotter;
	
	//Not necessary for Sim processes - purely for GUI charts
	private CrossSection.Integer educationCS;
	private CrossSection.Integer ageCS;

	private MeanArrayFunction fMeanEducation;				
	private MultiTraceFunction.Integer fTracePopSize;			//TODO: Change this to a series or timeseries?
	private MultiTraceFunction.Integer fTraceLifeExpectancy;
	private MultiTraceFunction.Integer fAvgAdultIncome;
	

	public THIMObserver(SimulationManager manager, SimulationCollectorManager collectorManager) {
		super(manager, collectorManager);
	}
		
	
	////////////////////////////////////////////////////////////////////////
	// Manager
	////////////////////////////////////////////////////////////////////////

	public void buildObjects() {
		if(observerOn) {

			final THIMModel model = (THIMModel) collector.getManager();

			//Non-essential for Sim Processes, but possibly interesting for analysis////////////////////

			fTracePopSize = new MultiTraceFunction.Integer(this, THIMObserver.Variables.populationSize);	
			fTraceLifeExpectancy = new MultiTraceFunction.Integer(this, THIMObserver.Variables.lifeExpectancy);
			fAvgAdultIncome = new MultiTraceFunction.Integer(this, THIMObserver.Variables.avgAdultIncome);
			
			educationCS = new CrossSection.Integer(model.getSims(), Sim.Variables.yearsInEducation);
			fMeanEducation = new MeanArrayFunction(educationCS);

			ageCS = new CrossSection.Integer(model.getSims(), Sim.Variables.age);		//TODO: Currently used in Observer charts, but replace with avgAgeWhenSimsDie (and avgCumulativeHealthyYears)
			
			agePlotter = new TimeSeriesSimulationPlotter("Mean Age, Life Expectancy & Years In Education", "years");
			agePlotter.addSeries("mean age", new MeanArrayFunction(ageCS));
			agePlotter.addSeries("years in education", new MeanArrayFunction(educationCS));
			agePlotter.addSeries("life expectancy", (IIntSource) fTraceLifeExpectancy);			
			GuiUtils.addWindow(agePlotter, 250, 90, 500, 500);

			incomePlotter = new TimeSeriesSimulationPlotter("Mean Income for adult sims and City Size", "$, number of Sims");
			incomePlotter.addSeries("city size", (IIntSource) fTracePopSize);
			incomePlotter.addSeries("adult income", (IIntSource) fAvgAdultIncome);
			GuiUtils.addWindow(incomePlotter, 750, 90, 500, 500);

			healthPlotter = new TimeSeriesSimulationPlotter("Mean Health Index", "index");
			healthPlotter.addSeries("health index", new MeanArrayFunction(new CrossSection.Double(model.getSims(), Sim.Variables.healthIndex)));
			GuiUtils.addWindow(healthPlotter, 1250, 90, 500, 500);

			log.debug("Observer objects created");
		}
	}
	
	public void buildSchedule() {

		if(observerOn) {
			//Implementation using event groups
			EventGroup eventGroup = new EventGroup();

			eventGroup.addEvent(agePlotter, CommonEventType.Update);
			eventGroup.addEvent(incomePlotter, CommonEventType.Update);
			eventGroup.addEvent(healthPlotter, CommonEventType.Update);
			getEngine().getEventList().scheduleRepeat(eventGroup, 0., Order.AFTER_ALL.getOrdering()-1, displayFrequency);

			log.debug("Observer schedule created");

		}
		
	}
	
	//////////////////////////////////////////////////////////////////////
	// IIntSource implementation
	//////////////////////////////////////////////////////////////////////
	
	public enum Variables {
		populationSize,
		lifeExpectancy,
		avgAdultIncome,
	}
	
	public int getIntValue(Enum<?> variableID) {
		switch ((Variables) variableID) {

		case populationSize:
			return ((THIMModel) collector.getManager()).getSims().size();
		case lifeExpectancy:
			return (int) ((THIMModel) collector.getManager()).getStats().getLifeExpectancyAndReset();
		case avgAdultIncome:
			return (int) ((THIMModel) collector.getManager()).getStats().getAvgAdultIncome();
		
		default:
			throw new IllegalArgumentException("Unsupported variable " + variableID.name() + " in THIMCollector#getIntValue");
		}
	}
		
	
	//////////////////////////////////////////////////////////////////////////////
	// Access methods
	//////////////////////////////////////////////////////////////////////////////


	public Double getDisplayFrequency() {
		return displayFrequency;
	}

	public void setDisplayFrequency(Double displayFrequency) {
		this.displayFrequency = displayFrequency;
	}

	public Boolean getObserverOn() {
		return observerOn;
	}

	public void setObserverOn(Boolean observerOn) {
		this.observerOn = observerOn;
	}

}	