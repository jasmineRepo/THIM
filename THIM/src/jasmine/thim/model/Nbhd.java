package jasmine.thim.model;

import microsim.data.db.PanelEntityKey;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

@Entity
public class Nbhd {
	
	public static long nbhdIdCounter = 0;
	
	@Id
	private PanelEntityKey key;

	@Column(name="number_of_workers")
	private int numWorkingResidents;				//Number of sims that live in this nbhd whose age >= their yearsInEducation, i.e. they have finished school and are now receiving an income.

	@Column(name="average_adult_income")
	private double avgNbhdAdultIncome;		//This is defined as "average income for all sims age >= MinAgeToReproduce in the nbhd for the most recent complete calendar year

	@Transient
	private double nbhdAdultIncomeRelativeToCityAvg;	
	
	//For output tables
	@Column(name="occupancy_factor")
	private double nbhdOccupancyFactor;
	
	@Column(name="average_education")
	private double avgNbhdEducation;
	
	@Column(name="average_health")
	private double avgNbhdHealthIndex;
	
	@Column(name="adult_count")
	private int numAdultResidents;
	
	@Column(name="child_count")
	private int numChildResidents;
	
	@Column(name="average_age")
	private double avgNbhdAge;

	///////////////////////////////////////////////////////////////////////////
	// Constructor
	///////////////////////////////////////////////////////////////////////////
	
	public Nbhd() {
		super();		
	}
	
	public Nbhd( long idNumber ) {
		this();
		key = new PanelEntityKey(idNumber);
		numWorkingResidents = 0;
	}
	
	
	/////////////////////////////////////////////////////////////////////
	// Access methods
	/////////////////////////////////////////////////////////////////////
	
	public PanelEntityKey getKey() {
		return key;
	}
	public int getNumWorkingResidents() {
		return numWorkingResidents;
	}

	public void setNumWorkingResidents(int numWorkingResidents) {
		this.numWorkingResidents = numWorkingResidents;
	}

	public double getAvgNbhdAdultIncome() {
		return avgNbhdAdultIncome;
	}

	public void setAvgNbhdAdultIncome(double avgNbhdAdultIncome) {
		this.avgNbhdAdultIncome = avgNbhdAdultIncome;
	}

	public double getNbhdAdultIncomeRelativeToCityAvg() {
		return nbhdAdultIncomeRelativeToCityAvg;
	}

	public void setNbhdAdultIncomeRelativeToCityAvg(
			double nbhdAdultIncomeRelativeToCityAvg) {
		this.nbhdAdultIncomeRelativeToCityAvg = nbhdAdultIncomeRelativeToCityAvg;
	}

	public double getNbhdOccupancyFactor() {
		return nbhdOccupancyFactor;
	}

	public void setNbhdOccupancyFactor(double nbhdOccupancyFactor) {
		this.nbhdOccupancyFactor = nbhdOccupancyFactor;
	}

	public double getAvgNbhdEducation() {
		return avgNbhdEducation;
	}

	public void setAvgNbhdEducation(double avgNbhdEducation) {
		this.avgNbhdEducation = avgNbhdEducation;
	}

	public double getAvgNbhdHealthIndex() {
		return avgNbhdHealthIndex;
	}

	public void setAvgNbhdHealthIndex(double avgNbhdHealthIndex) {
		this.avgNbhdHealthIndex = avgNbhdHealthIndex;
	}

	public int getNumAdultResidents() {
		return numAdultResidents;
	}

	public void setNumAdultResidents(int numAdultResidents) {
		this.numAdultResidents = numAdultResidents;
	}

	public int getNumChildResidents() {
		return numChildResidents;
	}

	public void setNumChildResidents(int numChildResidents) {
		this.numChildResidents = numChildResidents;
	}

	public double getAvgNbhdAge() {
		return avgNbhdAge;
	}

	public void setAvgNbhdAge(double avgNbhdAge) {
		this.avgNbhdAge = avgNbhdAge;
	}

}
