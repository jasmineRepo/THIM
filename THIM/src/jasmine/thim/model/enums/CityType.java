package jasmine.thim.model.enums;

public enum CityType {		//As defined in the THIM paper.
	C,							//(Canada?)
	U,							//(USA?)
	C_NoMortalityFactors,		//Regression co-efficients for Mortality set to zero, so only average mortality hazard used for each Sim.  The rest of the parameters are the same as C.
	U_NoMortalityFactors,		//Regression co-efficients for Mortality set to zero, so only average mortality hazard used for each Sim.  The rest of the parameters are the same as U.
	ModGen,						//Set of parameters placed on the uSask GitLab server (thim-master/modgen/src/Base(Parameters).dat) on March 1st 2015
	M100nbhds,					//Like ModGen parameters, except for having 100 nbhds
}
