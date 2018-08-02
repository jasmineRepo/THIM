package jasmine.thim.algorithms;

import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;

public class LifetimeEarningsHealthSearch {

	public static void incrementValue(MultiKeyMap map, double earnings, double health) {		
		for (MapIterator iterator = map.mapIterator(); iterator.hasNext();) {
			iterator.next();
			MultiKey mk = (MultiKey) iterator.getKey();
			int earningsFrom = (Integer) mk.getKey(0);
			int earningsTo = (Integer) mk.getKey(1);
			double healthFrom = ((Number) mk.getKey(2)).doubleValue();
			double healthTo = ((Number) mk.getKey(3)).doubleValue();
			
			if ((earnings >= earningsFrom) && (earnings < earningsTo) && (health > healthFrom) && (health <= healthTo)) {		//N.B. Sims must have a strictly positive lifetimeAverageHealth as they are initialised with a positive healthIndex (Sims whose health is updated to healthIndex = 0 should die at the next time-step).  Hence the inequalities on the healthFrom and healthTo. 
				int previousValue = ((Number) map.get(mk)).intValue();
				map.put(mk, previousValue + 1);			//Increment histogram
				return;
			}
		}
				
		throw new IllegalArgumentException("Lifetime Earnings " + earnings + " and lifetime health " + health + " cannot be mapped in incrementValue");
	}
	
}