package model;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import model.LineState.cacheSlotState;

import utils.Utile;

/**
 * Data and metadata part of the L1 cache.
 * Partially taken from soclib.
 * @author QLM
 *
 */
public class CacheL1 {

	long [] r_data;
	long [] r_tag;
	cacheSlotState [] r_state;
	boolean [] r_lru;
	boolean [] r_dirty;
	boolean [] r_exclu;
	
	private int traceLevel = 1;

	int m_id;
	int m_ways;
	int m_sets;
	int m_words;

	AddressMaskingTable m_x;
	AddressMaskingTable m_y;
	AddressMaskingTable m_z;

	Long cache_data(int way, long set, long word) {
		return r_data[(way * m_sets * m_words) + ((int) set * m_words)
				+ (int) word];
	}

	void set_cache_data(int way, long set, long word, long val) {
		r_data[(way * m_sets * m_words) + ((int) set * m_words)
				+ (int) word] = val;
	}

	Long cache_tag(int way, long set) {
		return r_tag[(way * m_sets) + (int) set];
	}

	void set_cache_tag(int way, long set, long val) {
		r_tag[(way * m_sets) + (int) set] = val;
	}

	Boolean cache_lru(int way, long set) {
		return r_lru[(way * m_sets) + (int) set];
	}

	void set_cache_lru(int way, long set, boolean val) {
		r_lru[(way * m_sets) + (int) set] = val;
	}

	Boolean cache_dirty(int way, long set) {
		return r_dirty[(way * m_sets) + (int) set];
	}

	void set_cache_dirty(int way, long set, boolean val) {
		r_dirty[(way * m_sets) + (int) set] = val;
	}

	Boolean cache_exclu(int way, long set) {
		return r_exclu[(way * m_sets) + (int) set];
	}

	void set_cache_exclu(int way, long set, boolean val) {
		r_exclu[(way * m_sets) + (int) set] = val;
	}

	cacheSlotState cache_state(int way, long set) {
		return r_state[(way * m_sets) + (int) set];
	}

	void set_cache_state(int way, long set, cacheSlotState val) {
		r_state[(way * m_sets) + (int) set] = val;
	}

	void cache_set_lru(int way, long set) {
		int way2;

		set_cache_lru(way, set, true);

		for (way2 = 0; way2 < m_ways; way2++) {
			if (cache_lru(way2, set) == false) {
				return;
			}
		}
		// if all lines are new, they all become old
		for (way2 = 0; way2 < m_ways; way2++) {
			set_cache_lru(way2, set, false);
		}
	}

	CacheL1(String name, int id, int nways, int nsets, int nwords) {
		
		this.m_id = id;

		this.m_ways = nways;
		this.m_sets = nsets;
		this.m_words = nwords;

		m_x = new AddressMaskingTable(Utile.log2(nwords), 2); // 2 = log2(4) (1 word)
		m_y = new AddressMaskingTable(Utile.log2(nsets), Utile.log2(nwords) + 2);
		m_z = new AddressMaskingTable(8 * 8 - Utile.log2(nsets)
				- Utile.log2(nwords) - 2, Utile.log2(nsets)
				+ Utile.log2(nwords) + 2);
		// assert(IS_POW_OF_2(nways));
		// assert(IS_POW_OF_2(nsets));
		// assert(IS_POW_OF_2(nwords));
		assert (nwords != 0);
		assert (nsets != 0);
		assert (nways != 0);
		assert (nwords <= 64);
		assert (nsets <= 1024);
		assert (nways <= 16);

		if (traceLevel > 4) {
			System.out.println("constructing " + name);
			System.out.println(" - nways  = " + nways);
			System.out.println(" - nsets  = " + nsets);
			System.out.println(" - nwords = " + nwords);
			System.out.println(" m_x: " + m_x);
			System.out.println(" m_y: " + m_y);
			System.out.println(" m_z: " + m_z);
			System.out.println();
		}
		r_data = new long[nways * nsets * nwords];
		r_tag = new long[nways * nsets];
		r_state = new cacheSlotState[nways * nsets];
		r_lru = new boolean[nways * nsets];
		r_dirty = new boolean[nways * nsets];
		r_exclu = new boolean[nways * nsets];
		
	}

	void reset() {
		for (int way = 0; way < m_ways; way++) {
			for (int set = 0; set < m_sets; set++) {
				for (int word = 0; word < m_words; word++) {
					set_cache_data(way, set, word, 0);
				}
				set_cache_tag(way, set, 0);
				set_cache_state(way, set, cacheSlotState.EMPTY);
				set_cache_lru(way, set, false);
				set_cache_dirty(way, set, false);
				set_cache_exclu(way, set, false);

			}
		}
	}

	boolean isSameLine(long ad1, long ad2) {
		long tag1 = m_z.get(ad1);
		long tag2 = m_z.get(ad2);
		long set1 = m_y.get(ad1);
		long set2 = m_y.get(ad2);
		return (tag1 == tag2 && set1 == set2);
	}

	// //////////////////////////////////////////////////////////////////
	// Read a single 32 bits word when the ZOMBIE state is used.
	// Both data and directory are accessed.
	// returns the access status in the state argument:
	// - VALID : (matching tag) and (state == VALID)
	// - ZOMBIE : (matching tag) and (state == ZOMBIE)
	// - MISS : no matching tag or EMPTY state
	// If VALID or ZOMBIE, returns true, and false otherwise
	// //////////////////////////////////////////////////////////////////
	boolean read(long ad, List<Long> dt, LineState state) {
		long tag = m_z.get(ad);
		long set = m_y.get(ad);
		long word = m_x.get(ad);
		//System.out.println("   read ad = 0x" + Long.toHexString(ad) + " - word = " + word);

		// default return values
		state.state = cacheSlotState.EMPTY;
		dt.clear();

		for (int way = 0; way < m_ways; way++) {
			if (tag == cache_tag(way, set)) {
				if (cache_state(way, set) == cacheSlotState.VALID) {
					state.state = cacheSlotState.VALID;
					state.dirty = cache_dirty(way, set);
					state.exclu = cache_exclu(way, set);
					dt.add(cache_data(way, set, word));
					System.out.println("READ SET : "+set+" WAY : "+way+" WORD : "+word);
					cache_set_lru(way, set);
					return true;
				}
				else if (cache_state(way, set) == cacheSlotState.ZOMBI) {
					state.state = cacheSlotState.ZOMBI;
					return true;
				}
			}
		}
		return false;
	}

	// /////////////////////////////////////////////////////////////////////////////
	// Checks the cache state for a given address.
	// Only the directory is accessed.
	// Returns the access status in the state argument:
	// - VALID if (matching tag) and (state == VALID)
	// - ZOMBIE if (matching tag) and (state == ZOMBIE)
	// - EMPTY if no match or (state == EMPTY)
	// The selected way, set and first word index are returned if not empty.
	// This function can be used when we need to access the directory
	// while we write in the data part with a different address in the same
	// cycle.
	// /////////////////////////////////////////////////////////////////////////////
	void read_dir(long ad, LineState state) {
		long ad_tag = m_z.get(ad);
		long ad_set = m_y.get(ad);
		// long ad_word = m_x.get(ad);

		for (int _way = 0; _way < m_ways; _way++) {
			if ((ad_tag == cache_tag(_way, ad_set))
					&& (cache_state(_way, ad_set) != cacheSlotState.EMPTY)) {
				state.state = cache_state(_way, ad_set);
				state.dirty = cache_dirty(_way, ad_set);
				state.exclu = cache_exclu(_way, ad_set);
				return;
			}
		}

		// return value if not (VALID or ZOMBIE)
		state.state = cacheSlotState.EMPTY;
	}

	void write_dir(long ad, LineState state) {
		long ad_tag = m_z.get(ad);
		long ad_set = m_y.get(ad);
		// long ad_word = m_x.get(ad);

		for (int _way = 0; _way < m_ways; _way++) {
			if ((ad_tag == cache_tag(_way, ad_set))
					&& (cache_state(_way, ad_set) != cacheSlotState.EMPTY)) {
				set_cache_state(_way, ad_set, state.state);
				set_cache_dirty(_way, ad_set, state.dirty);
				set_cache_exclu(_way, ad_set, state.exclu);
				return;
			}
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////
	// This function selects a victim slot in an associative set.
	// It can fail if all ways are in ZOMBIE state.
	// - we search first an EMPTY slot
	// - if no empty slot, we search an OLD slot not in ZOMBIE state,
	// - if not found, we take the first not ZOMBIE slot.
	// ////////////////////////////////////////////////////////////////////////////////
	CacheAccessResult readSelect(long ad) {
		long _set = m_y.get(ad);

		CacheAccessResult result = new CacheAccessResult();
		result.found = false;
		result.victimFound = false;
		result.victimAddress = 0;
		result.victimDirty = false;
		result.data = null;

		// Search first empty slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (cache_state(_way, _set) == cacheSlotState.EMPTY) {
				result.found = true; // Empty slot: no victim
				return result;
			}
		}

		// Search first not zombie old slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (!cache_lru(_way, _set)
					&& (cache_state(_way, _set) != cacheSlotState.ZOMBI)) {
				result.found = true;
				result.victimFound = true;
				result.victimAddress = (cache_tag(_way, _set) * m_sets + _set) * m_words * 4;
				result.victimDirty = cache_dirty(_way, _set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(cache_data(_way, _set, word));
					}
				}
				return result;
			}
		}

		// Search first not zombie slot
		for (int _way = 0; _way < m_ways && !(result.found); _way++) {
			if (cache_state(_way, _set) != cacheSlotState.ZOMBI) {
				result.found = true;
				result.victimFound = true;
				result.victimAddress = (cache_tag(_way, _set) * m_sets + _set)
						* m_words * 4;
				result.victimDirty = cache_dirty(_way, _set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(cache_data(_way, _set, word));
					}
				}
				return result;
			}
		}
		// no slot found
		return result;
	}

	/* when called, it must be guaranteed that there is room? */
	void set_line(long ad, List<Long> buf, boolean exclu) {
		long _set = m_y.get(ad);
		long tag = m_z.get(ad);


		// Search first empty slot
		for (int _way = 0; _way < m_ways; _way++) {
			if (cache_state(_way, _set) == cacheSlotState.EMPTY) {
				set_cache_tag(_way, _set, tag);
				set_cache_state(_way, _set, cacheSlotState.VALID);
				set_cache_exclu(_way, _set, exclu);
				cache_set_lru(_way, _set);
				for (int _word = 0; _word < m_words; _word++) {

					set_cache_data(_way, _set, _word, buf.get(_word));
				}
				return;
			}
		}

		// Search first not zombie old slot
		for (int _way = 0; _way < m_ways; _way++) {
			if (!cache_lru(_way, _set)
					&& (cache_state(_way, _set) != cacheSlotState.ZOMBI)) {

				set_cache_tag(_way, _set, tag);
				set_cache_state(_way, _set, cacheSlotState.VALID);
				set_cache_exclu(_way, _set, exclu);
				cache_set_lru(_way, _set);

				for (int _word = 0; _word < m_words; _word++) {
					set_cache_data(_way, _set, _word, buf.get(_word));
					System.out.println("PASSE PAR LA, DATA :"+ buf.get(_word)+" SET : "+_set + " WAY : "+_way + " WORD : "+_word);
				}
				return;
			}
		}

		assert (false);

	}

	void fileTrace(String filename) {
		PrintWriter content = null;
		try {
			content = new PrintWriter(new BufferedWriter(new FileWriter(
					filename)));
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (int nway = 0; nway < m_ways; nway++) {
			for (int nset = 0; nset < m_sets; nset++) {
				content.println(cache_state(nway, nset) + " / ");
				content.printf("way %d / ", (int) nway);
				content.printf("set %d / ", (int) nset);
				content.printf("@ = %08zX / ",
						((cache_tag(nway, nset) * m_sets + nset) * m_words * 4));
				for (int nword = m_words; nword > 0; nword--) {
					long data = cache_data(nway, nset, nword - 1);
					content.printf("%08X ", data);
				}
				content.printf("\n");
			}
		}
		content.close();
	}

	void printTrace() {
		System.out.printf("STATE | D | X | Way | Set | Address    | Data\n");
		for (int way = 0; way < m_ways; way++) {
			for (int set = 0; set < m_sets; set++) {
				long addr = ((cache_tag(way, set)) * m_words * m_sets + m_words * set) * 4;
				int d = (cache_dirty(way, set) ? 1 : 0);
				int x = (cache_exclu(way, set) ? 1 : 0);
				System.out.printf("%s | %d | %d | %3d | %3d | 0x%-8x",
						cache_state(way, set), d, x, way, set, addr);

				for (int word = 0; word < m_words; word++) {
					System.out.printf(" | 0x%-8x", cache_data(way, set, word));
				}
				System.out.println();
			}
		}
	}

	// The most general case must include the line being dirty and so
	// a buffer to copy it back into memory
	CacheAccessResult inval(long ad, boolean full_inval) {

		long tag = m_z.get(ad);
		long set = m_y.get(ad);

		CacheAccessResult result = new CacheAccessResult();

		result.victimFound = false;
		result.victimAddress = 0;
		result.victimDirty = false;
		result.data = null;

		for (int way = 0; way < m_ways; way++) {
			if ((tag == cache_tag(way, set))
					&& (cache_state(way, set) == cacheSlotState.VALID)) {
				result.victimFound = true;
				result.victimAddress = (cache_tag(way, set) * m_sets + set)
						* m_words * 4;
				result.victimDirty = cache_dirty(way, set);
				if (result.victimDirty) {
					result.data = new ArrayList<Long>();
					for (int word = 0; word < m_words; word++) {
						result.data.add(cache_data(way, set, word));
					}
				}
				if (full_inval) {
					set_cache_state(way, set, cacheSlotState.EMPTY);
					set_cache_lru(way, set, false);
				}
				set_cache_exclu(way, set, false);
				set_cache_dirty(way, set, false);
				return result;
			}
		}
		return result;
	}

	void write(long ad, long dt) {
		long tag = m_z.get(ad);
		long set = m_y.get(ad);
		long word = m_x.get(ad);

		for (int way = 0; way < m_ways; way++) {
			if ((tag == cache_tag(way, set))
					&& (cache_state(way, set) == cacheSlotState.VALID)) {
				assert (cache_exclu(way, set));
				set_cache_data(way, set, word, dt);
				cache_set_lru(way, set);
				set_cache_dirty(way, set, true);
			}
		}
		assert (false);
	}

	void write(long ad, long dt, int be) {
		long tag = m_z.get(ad);
		long set = m_y.get(ad);
		long word = m_x.get(ad);

		for (int way = 0; way < m_ways; way++) {
			if ((tag == cache_tag(way, set))
					&& (cache_state(way, set) == cacheSlotState.VALID)) {
				System.out.println("\t\t\t\t\t\t coucou");
				assert (cache_exclu(way, set));
				long mask = Utile.be2mask(be);
				long prev = cache_data(way, set, word);
				set_cache_data(way, set, word, (mask & dt) | (~mask & prev));
				cache_set_lru(way, set);
				set_cache_dirty(way, set, true);
				return;
			}
		}
		assert (false);
	}

}
