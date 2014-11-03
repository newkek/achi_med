package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import utils.Utile;

/**
 * Simple class for a memory constituted of segments. It provides facilities to access data and metadata.
 * @author QLM
 *
 */
public class Ram {
	
	enum BlockState {
		INVALID, VALID, EXCLUSIVE, MODIFIED, ZOMBIE,
	}
	
	int m_id;
	int m_words;
	int m_nbseg;
	int m_latency;
	
	Vector<Segment> m_seglist;
	Vector<long []> m_ram;
	long [] m_nbsets;
	
	Vector<CopiesList []> r_copies;
	Vector<BlockState []> r_state;
	
	AddressMaskingTable m_y;
	
	
	public Ram(String name, int id, int nwords, Vector<Segment> seglist, int latency) {
		m_id = id;
		m_seglist = seglist;
		m_words = nwords;
		m_latency = latency;
		m_nbseg = seglist.size();
		m_y = new AddressMaskingTable(8 * 8 - Utile.log2(nwords) - 2, // 2 = log2(sizeof(word))
				Utile.log2(nwords) + 2);
		
		assert (!seglist.isEmpty()) : "Ram error : no segment allocated";
		
		for (Segment seg : m_seglist) {
			System.out.println(seg);
		}
		
		// memory allocation
		m_ram = new Vector<long []>();
		m_nbsets = new long[m_nbseg];
		r_copies = new Vector<CopiesList []>();
		r_state = new Vector<BlockState []>();
		
		int i = 0;
		for (Segment seg : m_seglist) {
			m_ram.add(new long[(seg.size() + 3) / 4]);
			m_nbsets[i] = seg.size() >> (2 + Utile.log2(m_words));
			r_copies.add(new CopiesList[(int) m_nbsets[i]]);
			for (int j = 0; j < m_nbsets[i]; j++) {
				r_copies.get(i)[j] = new CopiesList();
			}
			r_state.add(new BlockState[(int) m_nbsets[i]]);
			i++;
		}
		

		reset();
	}
	

	void reset() {
		for (int seg = 0; seg < m_nbseg; seg++) {
			for (int i = 0; i < m_nbsets[seg]; i++) {
				r_copies.get(seg)[i].removeAll();
				r_state.get(seg)[i] = BlockState.VALID;
			}
			for (int i = 0; i < (m_seglist.get(seg).size() + 3) / 4; i++) {
				m_ram.get(seg)[i] = 0;
			}
		}
	}
	

	CopiesList copies(long addr) {
		long set = m_y.get(addr);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return r_copies.get(i)[(int) set];
			}
		}
		assert (false);
		return null;
	}
	

	BlockState state(long addr) {
		long set = m_y.get(addr);
		System.out.println("   addr : 0x" + Long.toHexString(addr) + " - set = " + set);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return r_state.get(i)[(int) set];
			}
		}
		assert (false);
		return BlockState.INVALID;
	}
	

	void setState(long addr, BlockState bs) {
		long set = m_y.get(addr);
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				r_state.get(i)[(int) set] = bs;
				return;
			}
		}
		assert (false);
	}
	

	boolean write(long addr, long wdata, int be) {
		long mask;
		long old_val, new_val;
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				mask = Utile.be2mask(be);
				old_val = m_ram.get(i)[index];
				new_val = wdata;
				m_ram.get(i)[index] = (old_val & ~mask) | (new_val & mask);
				return true;
			}
		}
		return false;
	}
	

	boolean write_line(long addr, List<Long> wdata) {
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				for (int word = 0; word < m_words; word++) {
					m_ram.get(i)[index + word] = wdata.get(word);
				}
				return true;
			}
		}
		return false;
	}
	

	boolean hasCopy(long addr, int cache_id) {
		return copies(addr).hasCopy(cache_id);
	}
	

	boolean hasOtherCopy(long addr, int cache_id) {
		return copies(addr).hasOtherCopy(cache_id);
	}
	

	void addCopy(long addr, int cache_id) {
		copies(addr).add(cache_id);
	}
	

	void removeCopy(long addr, int cache_id) {
		copies(addr).remove(cache_id);
	}
	

	void removeAllCopies(long addr) {
		copies(addr).removeAll();
	}
	

	int nbCopies(long addr) {
		return copies(addr).nbCopies();
	}
	

	CopiesList getCopies(long addr) {
		return copies(addr);
	}
	

	BlockState getState(long addr) {
		return state(addr);
	}
	

	boolean isMod(long addr) {
		return state(addr) == BlockState.MODIFIED;
	}
	

	boolean isExclu(long addr) {
		return state(addr) == BlockState.EXCLUSIVE;
	}
	

	boolean containsAddr(long addr) {
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				return true;
			}
		}
		return false;
	}
	

	boolean getLine(long addr, List<Long> rdata) {
		rdata.clear();
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				for (int word = 0; word < m_words; word++) {
					rdata.add(m_ram.get(i)[index + word]);
				}
				return true;
			}
		}
		return false;
	}
	

	List<Long> getLine(long addr) {
		List<Long> res = new ArrayList<Long>();
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				for (int word = 0; word < m_words; word++) {
					res.add(m_ram.get(i)[index + word]);
				}
				return res;
			}
		}
		return null;
	}
	

	boolean read(long addr, List<Long> rdata) {
		for (int i = 0; i != m_nbseg; i++) {
			if (m_seglist.get(i).contains(addr)) {
				int index = (int) ((addr - m_seglist.get(i).baseAddress()) / 4);
				rdata.add(m_ram.get(i)[index]);
				return true;
			}
		}
		return false;
	}
	
}
