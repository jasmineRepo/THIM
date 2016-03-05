package jasmine.thim.experiment;

import jasmine.thim.model.THIMModel;
import microsim.engine.MultiRun;
import microsim.engine.SimulationEngine;
import microsim.gui.shell.MultiRunFrame;

public class THIMMultiRun extends MultiRun {

	public static boolean executeWithGui = true;

	private static int maxNumberOfRuns = 12;

	private Long counter = 1L;
	
	private Integer randomSeed = 1;
	
	public static void main(String[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-n")){
				
				try {
					maxNumberOfRuns = Integer.parseInt(args[i + 1]);
			    } catch (NumberFormatException e) {
			        System.err.println("Argument " + args[i + 1] + " must be an integer reflecting the maximum number of runs.");
			        System.exit(1);
			    }
				
				i++;
			}
			else if (args[i].equals("-g")){
				executeWithGui = Boolean.parseBoolean(args[i + 1]);
				i++;
			}
		}
		
		SimulationEngine engine = SimulationEngine.getInstance();
		
		THIMMultiRun experimentBuilder = new THIMMultiRun();
		engine.setExperimentBuilder(experimentBuilder);					//This replaces the above line... but does it work?
		engine.setup();													//Do we need this?  Worked fine without it...

		if (executeWithGui)
			new MultiRunFrame(experimentBuilder, "THIM MultiRun", maxNumberOfRuns);
		else
			experimentBuilder.start();
	}

	@Override
	public void buildExperiment(SimulationEngine engine) {
		THIMModel model = new THIMModel();
		
		model.setMicrosimShellUse(false);
		model.setFixRandomSeed(true);
		model.setRandomSeedIfFixed(randomSeed);
		
		engine.addSimulationManager(model);
		
		THIMCollector collector = new THIMCollector(model);
		engine.addSimulationManager(collector);
		
	}
	
	@Override
	public boolean nextModel() {
		randomSeed++;
		
		counter++;

		if(counter <= maxNumberOfRuns) {
			return true;
		}
		else return false;
	}

	@Override
	public String setupRunLabel() {
		return "Run " + counter.toString();
	}

}
