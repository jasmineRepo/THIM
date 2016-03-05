package jasmine.thim.algorithms;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ExportBinTable {

	/**
	 * 
	 * Creates a comma-seperated value file of statistics in equal width bins based on data provided by arrays
	 * 
	 * @param filename - the name of the file.  If a file with the same name already exists, this method will create a new file with an incremented index
	 * @param fileHeader - the headings for the table columns
	 * @param bin_interval - the interval in relevant units that a bin represents
	 * @param dataArrays - the arrays carrying the data
	 * 
	 * @author Ross Richardson
	 * 
	 */
	public static void createCSV(File directory, String filename, String fileHeader, double bin_interval, Object[]...dataArrays) {

		//Fields for exporting tables to output .csv files 
		final String newLine = "\n";
		final String delimiter = ","; 

        FileWriter fileWriter = null; 
        try { 
        	//Creates file but checks whether a file with the same filename already exists - if so, increments the index until there is no file with the same filename. 
        	String newFilename = null;
        	int version = 1;
        	File f = new File(directory + File.separator + filename + version + ".csv");
        	while (f.exists())
        	{
        		version++;
        	    newFilename= filename + version;
        	    f = new File(directory + File.separator + newFilename + ".csv");
        	        
        	} 
        	f.createNewFile();
        	        	
        	//Set FileWriter and table headers
            fileWriter = new FileWriter(f);
            fileWriter.append(fileHeader.toString());
            fileWriter.append(newLine);
            
        	int maxObjectArrayLength = 0;
        	for(int index = 0; index < dataArrays.length; index++) {
        		if(dataArrays[index].length > maxObjectArrayLength) {
        			maxObjectArrayLength = dataArrays[index].length; 
        		}
        	}
        	
        	for(int age_bin_index = 0; age_bin_index < maxObjectArrayLength; age_bin_index++) {
        		fileWriter.append(String.valueOf("[" + (bin_interval * age_bin_index) + ";" + (bin_interval * (age_bin_index+1)) + "["));

        		for(int column = 0; column < dataArrays.length; column++) {
	            	fileWriter.append(delimiter);
        			if(age_bin_index < dataArrays[column].length) {
        				fileWriter.append(String.valueOf(dataArrays[column][age_bin_index]));
        			}
        		}
        		fileWriter.append(newLine);
        	}
            
        } catch (Exception e) { 
        	System.out.println("Error in FileWriter for THIMCollector#exportAgeTable()"); 
        	e.printStackTrace(); 
        } 
        finally {
        	try { 
        		fileWriter.flush(); 
        		fileWriter.close(); 
        	} catch (IOException e) { 
        			System.out.println("Error while flushing/closing fileWriter in THIMCollector#exportAgeTable()."); 
        			e.printStackTrace(); 
        	} 
        }
	
	}	
	
}
