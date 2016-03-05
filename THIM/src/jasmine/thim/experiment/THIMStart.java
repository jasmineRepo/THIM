package jasmine.thim.experiment;

import microsim.engine.ExperimentBuilder;
import microsim.engine.SimulationEngine;
import microsim.gui.shell.MicrosimShell;
import jasmine.thim.model.THIMModel;

public class THIMStart implements ExperimentBuilder {

	//Set default value to 50000.  Can override this either using the GUI, 
	//or if running in the command prompt by using the command '-p' followed by the desired population size.
	private static int initialPopulationSize = 50000;		

	private static boolean showMicrosimShellGui = true;
	
	private static boolean useDatabase = true;
		
	public static void main(String[] args) {
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-p")){
				
				try {
					initialPopulationSize = Integer.parseInt(args[i + 1]);
			    } catch (NumberFormatException e) {
			        System.err.println("Argument " + args[i + 1] + " must be an integer.");
			        System.exit(1);
			    }
				
				i++;
			}
			else if (args[i].equals("-g")){
				
				showMicrosimShellGui = Boolean.parseBoolean(args[i + 1]);
				i++;
			}
			else if (args[i].equals("-d")){
				
				useDatabase = Boolean.parseBoolean(args[i + 1]);
				i++;
			}

		}
		
		SimulationEngine engine = SimulationEngine.getInstance();
		MicrosimShell gui = null;
		if (showMicrosimShellGui) {
			gui = new MicrosimShell(engine);		
			gui.setVisible(true);
		}
		
		if(!useDatabase) {
			engine.setSilentMode(true);							//Turn off database mode
		}
//		engine.setBuilderClass(THIMStart.class);				//deprecated
		THIMStart experimentBuilder = new THIMStart();			//New version
		engine.setExperimentBuilder(experimentBuilder);
		engine.setup();
		if(!showMicrosimShellGui) {
			engine.startSimulation();		//Automatically start simulation running without using GUI controls.
		}

	}
	
	public void buildExperiment(SimulationEngine engine) {
		THIMModel model = new THIMModel();
		model.setMicrosimShellUse(showMicrosimShellGui);
		engine.addSimulationManager(model);
		
		THIMCollector collector = new THIMCollector(model);
		engine.addSimulationManager(collector);
		
		if(showMicrosimShellGui) {
			THIMObserver observer = new THIMObserver(model, collector);
			engine.addSimulationManager(observer);
		}
				
	}

	public static int getInitialPopulationSize() {
		return initialPopulationSize;
	}

	public static boolean isShowGui() {
		return showMicrosimShellGui;
	}

	public static boolean isUseDatabase() {
		return useDatabase;
	}

}	