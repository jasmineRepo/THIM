package jasmine.thim.experiment;

import microsim.annotation.GUIparameter;
import microsim.data.DataExport;
import microsim.data.MultiKeyCoefficientMap;
import microsim.engine.AbstractSimulationCollectorManager;
import microsim.engine.SimulationManager;
import microsim.event.EventListener;
import microsim.event.Order;
import microsim.event.SingleTargetEvent;
import jasmine.thim.algorithms.ExportBinTable;
import jasmine.thim.model.THIMModel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.log4j.Logger;

public class THIMCollector extends AbstractSimulationCollectorManager implements EventListener {
		
	private final static Logger log = Logger.getLogger(THIMCollector.class);

//	Needs to be in Model class as Statistics class needs it to determine when to record death data in Sim class
//	@GUIparameter(description="year in which to start collecting data to produce output tables")
//	private Integer recordDataAfterYear = 450;
//	
//	@GUIparameter(description="number of age bins")
//	private Integer numAgeBinsInTables = 20;
//	
//	private double numYearsInTableBins;				//The number of years of age range each bin contains (== max age / numAgeBinsInTables)

	@GUIparameter(description="produce output tables in .csv format")
	private boolean produceOutputTables = true;
	
	@GUIparameter(description="Export snapshots to database")
	private boolean exportToDatabase = false;

	@GUIparameter(description="Export snapshots to .csv files")
	private boolean exportToCSV = false;

	@GUIparameter(description="persists the individual sim data in the database")
	private boolean saveSimData = false;
	
	@GUIparameter(description="persists the nbhd data in the database")
	private boolean saveNbhdData = false;
	
	@GUIparameter(description="persists the aggregate city-level statistics in the database")
	private boolean saveCityData = false;
	
	@GUIparameter(description="year to start persisting snapshots of data for export")
	private Integer yearToBeginDataSnapshots = 0;		//Allows the user to control when the simulation starts exporting to the database, in case they want to delay exporting until after an initial 'burn-in' period.	
	
	@GUIparameter(description="number of years between snapshots")
	private Integer numYearsBetweenDatabaseSnapshots = 1;
	
//	@GUIparameter(description="number of quantiles for nbhd table")
//	private Integer numQuantilesForNbhdTable = 7;		//Attempt to capture a range of quantiles as in the ModGen ad-hoc nbhd rank numbers...
	
	DataExport simsOutput;
	DataExport nbhdsOutput;
	DataExport statsOutput;
	
	public THIMCollector(SimulationManager manager) {
		super(manager);
	}
	
	//Fields for exporting tables to output .csv files 
	final String newLine = "\n";
	final String delimiter = ",";
	final String filenameStructure = "pop" + ((THIMModel) getManager()).getStartingPopulationSize() + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.	
     

	/////////////////////////////////////////////////////////
	// Manager
	/////////////////////////////////////////////////////////

	public void buildObjects() {
		if(!THIMStart.isShowGui() && THIMStart.isUseDatabase()) {
			saveSimData = true;
			saveNbhdData = true;
			saveCityData = true;
		}
		if(saveSimData) {
			simsOutput = new DataExport(((THIMModel) getManager()).getSims(), exportToDatabase, exportToCSV);	
		}
		if(saveNbhdData) {
			nbhdsOutput = new DataExport(((THIMModel) getManager()).getNbhds(), exportToDatabase, exportToCSV);	
		}
		if(saveCityData) {
			statsOutput = new DataExport(((THIMModel) getManager()).getStats(), exportToDatabase, exportToCSV);	
		}		
	}
	
	public void buildSchedule() {

		if(saveCityData || saveNbhdData || saveSimData) {
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.DumpInfo), yearToBeginDataSnapshots, Order.BEFORE_ALL.getOrdering()+1, numYearsBetweenDatabaseSnapshots);		//Dump info from year 'yearDatabaseDumpStarts' onwards, with numYearsBetweenDatabaseDumps specifying the frequency of database dumps thereafter (nbhd and statistics only updated at start of new year, Sim data only updated once a year on their birth'day's (year + birthTimestamps), so once a year is minimum suitable frequency to dump to database

//			//Dump data to database at the (scheduled) end of the simulation
//			getEngine().getEventList().schedule(new SingleTargetEvent(this, Processes.DumpInfo), ((THIMModel) getManager()).getEndYear(), Order.BEFORE_ALL.getOrdering()+1, 0.);
		}
		
		if(produceOutputTables) {
			getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.ResetOutputStatistics), ((THIMModel) getManager()).getRecordDataAfterYear(), Order.BEFORE_ALL.getOrdering());		//Clear out previously accumulated stats (probably cheaper in terms of time than checking every year whether the statistics should be recorded, plus it requires simpler code).
			getEngine().getEventQueue().scheduleRepeat(new SingleTargetEvent(this, Processes.IncrementAverages), ((THIMModel) getManager()).getRecordDataAfterYear(), -1, 1.);		//At the start of the year (just after Statistics.updateStatistics() has been called), add the new data to the existing averages.
			getEngine().getEventQueue().scheduleOnce(new SingleTargetEvent(this, Processes.ProduceOutputTables), ((THIMModel) getManager()).getEndYear(), Order.AFTER_ALL.getOrdering()-1);		//Produce output just before terminating simulation
		}
	}
	

	////////////////////////////////////////////////////////////
	// Event Listener
	////////////////////////////////////////////////////////////
	
	public enum Processes {
		DumpInfo,
		ResetOutputStatistics,
		IncrementAverages,
		ProduceOutputTables
	}
	
	public void onEvent(Enum<?> type) {
		switch ((Processes) type) {
				
		case DumpInfo:
			if (saveSimData) {
				simsOutput.export();
			}
			if(saveNbhdData) {
				nbhdsOutput.export();
			}
			if (saveCityData) {
				statsOutput.export();
			}
			break;

		case ResetOutputStatistics:
			((THIMModel) getManager()).getStats().resetStatisticsForOutputTables();
			break;
			
		case IncrementAverages:
			((THIMModel) getManager()).getStats().incrementCityAndNbhdAverages();
			break;
			
		case ProduceOutputTables:
			calculateOutputStatistics();		//Calculate statistics to put in output tables
			produceTables();						//Create .csv files and export output statistics to them
			break;
			
		}
	}
	
	
	//////////////////////////////////////////////////////////
	// Methods to create output tables
	//////////////////////////////////////////////////////////
		
	private void calculateOutputStatistics() {			//Calculate required statistics to then be exported to .csv
		((THIMModel) getManager()).getStats().calculateStatisticsByAgeBins();

		((THIMModel) getManager()).getStats().calculateLongRunCityAndNbhdStatistics();

	}
	
	private void produceTables() {		//Export to the necessary data to .csv files in the working directory
		
		File directory = new File("JASmine_THIM_Results");
		if (!directory.exists()) {
			if (!directory.mkdir()) {
				System.out.println("Failed to create directory");
			}
		}
	
		/* Produce Avgs table
		 * Average income, average health, adult population, child population, yearly data (time-)averaged 
		 * over the period of analysis (i.e. cumulated and then divided by the number of years of recording).
		 */
		String name = "Avgs";		//The base name with which to produce the full filename in the line below.
		String filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented. 
		exportCityAvgsTable(directory, filename);
		
		//Produce age bin tables
		double age_bin_interval = ((THIMModel) getManager()).getAgeBinInterval();
	
		/* Produce Deaths By Age Bin table - total number of deaths by 5 year age groups (recall max age = 100).
		 * In fact in our model, Sims who turn 100 are scheduled to die on the next time-step (so there will be 
		 * some Sims in the [100, 105] age range who live to be 100.XXX with the decimal places depending on 
		 * how many time-steps per year there are. 
		 */
		name = "DeathsByAge";
		filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.
		String header = "Age,Deaths during interval,Average Age at Death";			//The column headings in the table
        Double[] avgAgeAtDeathData = ArrayUtils.toObject(((THIMModel) getManager()).getStats().getAverageAgeAtDeath());		//Need to pass as Objects not primitives, so convert here
        Integer[] numSimsAgeAtDeathData = ArrayUtils.toObject(((THIMModel) getManager()).getStats().getNumSimsAgeAtDeath());
        ExportBinTable.createCSV(directory, filename, header, age_bin_interval, numSimsAgeAtDeathData, avgAgeAtDeathData);
        
        //Produce Health Index by Age Bin table - average health status (in [0,1]) by 5 year age groups 
  		name = "HavgByAge";
  		filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.
  		header = "Age,mean health,Count";
        Double[] avgHealthByAgeData = ArrayUtils.toObject(((THIMModel) getManager()).getStats().getAverageHealthByAge());
        Integer[] numSimsByAgeData = ArrayUtils.toObject(((THIMModel) getManager()).getStats().getNumSimsByAge());
        ExportBinTable.createCSV(directory, filename, header, age_bin_interval, avgHealthByAgeData, numSimsByAgeData);        
        
        //Produce Income by Age Bin table - average income by 5 year age groups 
  		name = "YavgByAge";
  		filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.
  		header = "Age,mean income,Count";
        Double[] avgIncomeByAgeData = ArrayUtils.toObject(((THIMModel) getManager()).getStats().getAverageIncomeByAge());		
        ExportBinTable.createCSV(directory, filename, header, age_bin_interval, avgIncomeByAgeData, numSimsByAgeData);		//numSimsByAgeData defined in Health Index by Age table above.
        
        
        /* Produce LavgYLavgH
         * The central focus in this table is on lifetime average income versus lifetime 
         * average health � our �bottom line� health gradient, but from a full life course perspective.  For 
         * each simulated individual, total income over the entire lifetime is computed at the time of death, 
         * and divided by life length.  Similarly, total health over the entire lifetime is computed at the 
         * time of death, and divided by life length.
         */
  		name = "LavgYLavgH";
  		filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.
  		exportLifetimeIncomeAndHealthTable(directory, filename);

  		
  		/* Produce NbhdAvgs
  		 * This table is more complex because it shows averages for each of the nbhds over the period of analysis.  
  		 * In this case, we can focus on the quantiles across all nbhds, specifically if the nbhds are ordered 
  		 * by each of occupancy factor, adult pop, child pop, average health and average income, then the key 
  		 * results will be the min, mid, and max of the values, plus some other quantiles.
  		 * 
  		 * NOTE - rather than an ad-hoc list of quantiles that are ultimately dependent on the number of nbhds in
  		 * the simulation, we choose to show all ranks.
  		 */
  		name = "NbhdAvgs";
  		filename = filenameStructure + name + "_";		//The filename, indexed with initial population size information.  If a file with the same name already exists an index at the end of the filename will be incremented.
  		exportNbhdAvgsTable(directory, filename);
  		
	}
	
	
	
	private void exportNbhdAvgsTable(File directory, String filename) {

		LinkedHashMap<String, ArrayList<Double>> nbhdAvgsData = new LinkedHashMap<String, ArrayList<Double>>();
		//Convert double[] data to ArrayLists (to be later sorted), and place in map 
		nbhdAvgsData.put("Occupancy factor", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNbhdOccupancyFactor()))));
		nbhdAvgsData.put("Normalized average education", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getNormalisedAvgNbhdEducation()))));
		nbhdAvgsData.put("Average education", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNbhdEducation()))));
		nbhdAvgsData.put("Normalized average income", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getNormalisedAvgNbhdAdultIncome()))));
		nbhdAvgsData.put("Average income", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNbhdAdultIncome()))));
		nbhdAvgsData.put("Normalized average health", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getNormalisedAvgNbhdHealthIndex()))));
		nbhdAvgsData.put("Average health", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNbhdHealthIndex()))));
		nbhdAvgsData.put("Adult count", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNumberOfAdultsInNbhd()))));
		nbhdAvgsData.put("Child count", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNumberOfChildrenInNbhd()))));
		nbhdAvgsData.put("Average Age", new ArrayList<Double>(Arrays.asList(ArrayUtils.toObject(((THIMModel) getManager()).getStats().getLongRunAvgNbhdAge()))));
  		
  		//Sort data to get rankings
		for(ArrayList<Double> data : nbhdAvgsData.values()) {
			data.sort(null);
		}
  		
		String header = "Neighbourhood indicators,Selected Neighbourhood Ranks,Ranks Value";
		
        FileWriter fileWriter = null; 
        try { 
        	fileWriter = createFileWithIncrementedIndex(directory, filename, header);
        
        	//Add data: NOTE - rather than an ad-hoc list of quantiles that are ultimately dependent on 
        	//the number of nbhds in the simulation, we choose to show all ranks.
        	for(String nbhdIndicator : nbhdAvgsData.keySet()) {	
	        	for(Integer index = 0; index < nbhdAvgsData.get(nbhdIndicator).size(); index++) {		
	        		fileWriter.append(nbhdIndicator);
	        		fileWriter.append(delimiter);
	        		fileWriter.append("Rank=" + (index+1));
	        		if(index == 0) {
	        			fileWriter.append(" (Min)");
	        		} 
	        		else if(index == nbhdAvgsData.get(nbhdIndicator).size()-1) {
	        			fileWriter.append(" (Max)");
	        		}
	        		fileWriter.append(delimiter);
	        		fileWriter.append(String.valueOf(nbhdAvgsData.get(nbhdIndicator).get(index)));
	        		fileWriter.append(newLine);
	    		}
        	}        	

//        	//Add data - a subset of ranks
//        	for(String nbhdIndicator : nbhdAvgsData.keySet()) {
//        		
//        		LinkedHashSet<Integer> ranksSet = new LinkedHashSet<Integer>();		//Ensures no repeated elements
//        		int numNbhds = nbhdAvgsData.get(nbhdIndicator).size();
//        		
//        		float step = (float)(numNbhds-1)/(float)(numQuantilesForNbhdTable-1);
//        		for(int i = 0; i < numQuantilesForNbhdTable; i++) {
//        			int rank = (int)(step * i) + 1;
//        			ranksSet.add(rank);
//        		}
//        		
//        		for(Integer rank : ranksSet) {
//        			if(rank <= nbhdAvgsData.get(nbhdIndicator).size()) {
//	        			fileWriter.append(nbhdIndicator);
//		        		fileWriter.append(delimiter);
//		        		fileWriter.append("Rank=" + rank);
//		        		if(rank == 1) {
//		        			fileWriter.append(" (Min)");
//		        		} 
//		        		else if(rank == nbhdAvgsData.get(nbhdIndicator).size()) {
//		        			fileWriter.append(" (Max)");
//		        		}
//		        		fileWriter.append(delimiter);
//		        		fileWriter.append(String.valueOf(nbhdAvgsData.get(nbhdIndicator).get(rank-1)));		//Array index runs from 0 to size-1, so need to decrement by 1 to get appropriate value.
//		        		fileWriter.append(newLine);
//        			}
//        		}	
//        	}
	  	
        } catch (Exception e) { 
        	System.out.println("Error in FileWriter for THIMCollector#exportNbhdAvgsTable"); 
        	e.printStackTrace(); 
        } 
        finally {
        	try { 
        		fileWriter.flush(); 
        		fileWriter.close(); 
        	} catch (IOException e) { 
        			System.out.println("Error while flushing/closing fileWriter in THIMCollector#exportNbhdAvgsTable"); 
        			e.printStackTrace(); 
        	} 
        }        				
		
	}


	private void exportCityAvgsTable(File directory, String filename) {
			
		String header = "Average education,Average income,Average health,Adult Population,Child Population";			//The column headings in the table
		
        FileWriter fileWriter = null; 
        try { 
        	fileWriter = createFileWithIncrementedIndex(directory, filename, header);
      			
            //Add data
			fileWriter.append(String.valueOf(((THIMModel) getManager()).getStats().getLongRunAvgYearsInEducation()));
			fileWriter.append(delimiter);
			fileWriter.append(String.valueOf(((THIMModel) getManager()).getStats().getLongRunAvgAdultIncome()));
			fileWriter.append(delimiter);
			fileWriter.append(String.valueOf(((THIMModel) getManager()).getStats().getLongRunAvgHealth()));
			fileWriter.append(delimiter);
			fileWriter.append(String.valueOf(((THIMModel) getManager()).getStats().getLongRunAvgAdultPopulation()));
			fileWriter.append(delimiter);
			fileWriter.append(String.valueOf(((THIMModel) getManager()).getStats().getLongRunAvgChildPopulation()));
  			fileWriter.append(newLine);
	  	
        } catch (Exception e) { 
        	System.out.println("Error in FileWriter for THIMCollector#exportCityAvgsTable"); 
        	e.printStackTrace(); 
        } 
        finally {
        	try { 
        		fileWriter.flush(); 
        		fileWriter.close(); 
        	} catch (IOException e) { 
        			System.out.println("Error while flushing/closing fileWriter in THIMCollector#exportCityAvgsTable"); 
        			e.printStackTrace(); 
        	} 
        }        		
		
	}

	private void exportLifetimeIncomeAndHealthTable(File directory, String filename) {
		
  		MultiKeyCoefficientMap lifetimeIncomeAndHealth = ((THIMModel) getManager()).getStats().getLifetimeIncomeLifetimeHealthHistogram();
  		
		String header = "AvgLY,AvgLH,Count";
		
        FileWriter fileWriter = null; 
        try { 
        	fileWriter = createFileWithIncrementedIndex(directory, filename, header);

            //Sort the MultiKeys in ascending order first with first pair of keys, then with second pair of keys
            ArrayList<MultiKey> mkList = new ArrayList<MultiKey>(lifetimeIncomeAndHealth.keySet());
            Collections.sort(mkList, new Comparator<MultiKey>() {  		//Normal iteration through MultiKeys in a MultiKeyCoefficientMap is random.  This orders the MultiKeys by first sorting in ascending order of first key (the lower bound of the income interval), then in ascending order of third key (the lower bound of the health index interval) 
//            	@Override  
            	public int compare(MultiKey mk1, MultiKey mk2) {  
            		return new CompareToBuilder().append(((Number)mk1.getKey(0)).intValue(), ((Number)mk2.getKey(0)).intValue()).append(((Number)mk1.getKey(2)).doubleValue(), ((Number)mk2.getKey(2)).doubleValue()).toComparison();  
            	}  
            });           
            
            //Add data
	  		for(MultiKey mk : mkList) {
	  			
  				fileWriter.append(String.valueOf("[" + mk.getKey(0) + ";" + mk.getKey(1) + "["));
  				fileWriter.append(delimiter);
  				fileWriter.append(String.valueOf("[" + mk.getKey(2) + ";" + mk.getKey(3) + "["));
  				fileWriter.append(delimiter);
	  			fileWriter.append(String.valueOf(lifetimeIncomeAndHealth.get(mk)));
	  			fileWriter.append(newLine);
	  		}
	  	
        } catch (Exception e) { 
        	System.out.println("Error in FileWriter for THIMCollector#exportLifetimeIncomeAndHealthTable()"); 
        	e.printStackTrace(); 
        } 
        finally {
        	try { 
        		fileWriter.flush(); 
        		fileWriter.close(); 
        	} catch (IOException e) { 
        			System.out.println("Error while flushing/closing fileWriter in THIMCollector#exportLifetimeIncomeAndHealthTable()"); 
        			e.printStackTrace(); 
        	} 
        }        		
	}

	//Creates file but checks whether a file with the same filename already exists - if so, increments the index until there is no file with the same filename.        	
	private FileWriter createFileWithIncrementedIndex(File directory, String filename, String header) {

		FileWriter fileWriter = null; 
    	String newFilename = null;
    	int version = 1;
    	File f = new File(directory + File.separator + filename + version + ".csv");
    	while (f.exists())
    	{
    		version++;
    	    newFilename= filename + version;
    	    f = new File(directory + File.separator + newFilename + ".csv");
    	        
    	} 
    	
    	try {
			f.createNewFile();
			//Set FileWriter and table headers
	        fileWriter = new FileWriter(f);
	        fileWriter.append(header.toString());
	        fileWriter.append(newLine);

		} catch (IOException e) {
			System.out.println("Error in FileWriter in THIMCollector#createFileWithIncrementedIndex()");
			e.printStackTrace();
		}
    	        	    	
        return fileWriter;
	}


	//////////////////////////////////////////////////////////
	// Access methods
	//////////////////////////////////////////////////////////

	public Boolean getProduceOutputTables() {
		return produceOutputTables;
	}

	public void setProduceOutputTables(Boolean produceOutputTables) {
		this.produceOutputTables = produceOutputTables;
	}

	public boolean isExportToDatabase() {
		return exportToDatabase;
	}

	public void setExportToDatabase(boolean exportToDatabase) {
		this.exportToDatabase = exportToDatabase;
	}

	public boolean isExportToCSV() {
		return exportToCSV;
	}

	public void setExportToCSV(boolean exportToCSV) {
		this.exportToCSV = exportToCSV;
	}

	public boolean isSaveSimData() {
		return saveSimData;
	}

	public void setSaveSimData(boolean saveSimData) {
		this.saveSimData = saveSimData;
	}

	public boolean isSaveNbhdData() {
		return saveNbhdData;
	}

	public void setSaveNbhdData(boolean saveNbhdData) {
		this.saveNbhdData = saveNbhdData;
	}

	public boolean isSaveCityData() {
		return saveCityData;
	}

	public void setSaveCityData(boolean saveCityData) {
		this.saveCityData = saveCityData;
	}

	public Integer getYearToBeginDataSnapshots() {
		return yearToBeginDataSnapshots;
	}

	public void setYearToBeginDataSnapshots(Integer yearToBeginDataSnapshots) {
		this.yearToBeginDataSnapshots = yearToBeginDataSnapshots;
	}

	public Integer getNumYearsBetweenDatabaseSnapshots() {
		return numYearsBetweenDatabaseSnapshots;
	}

	public void setNumYearsBetweenDatabaseSnapshots(Integer numYearsBetweenDatabaseSnapshots) {
		this.numYearsBetweenDatabaseSnapshots = numYearsBetweenDatabaseSnapshots;
	}

//	public Integer getNumQuantilesForNbhdTable() {
//		return numQuantilesForNbhdTable;
//	}
//
//	public void setNumQuantilesForNbhdTable(Integer numQuantilesForNbhdTable) {
//		this.numQuantilesForNbhdTable = numQuantilesForNbhdTable;
//	}

}	