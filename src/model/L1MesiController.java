package model;

import java.util.ArrayList;
import java.util.List;

import utils.Utile;

import model.LineState.cacheSlotState;
import model.Request.cmd_t;

/**
 * This class implements a L1 MESI controller. The l1StartId purpose is to make a correspondence between the processor srcid,
 * ranging from 0 to nb_caches - 1, and the srcid on the network.
 * @author
 */
public class L1MesiController implements L1Controller {
	
	// Shouldn't be changed: a small value may create conflicts with processors srcid
	// a value giving srcid higher than 31 will not work
	// (A better implementation is possible)
	static final int l1StartId = 10;
	
	
	enum FsmState {
		/* To complete */
		FSM_IDLE,
	}
	
	int r_srcid; // Global initiator and target index
	int r_procid; // id of the associated processor
	
	boolean r_outdated;
	cmd_t r_cmd_req;
	long r_wb_addr;
	List<Long> r_wb_buf;
	boolean r_rsp_miss_ok;
	boolean r_current_wb;
	boolean r_processing_req;
	
	int m_words;
	int m_cycle;
	
	String m_name;
	
	CacheL1 m_cache_l1;
	Channel p_in_req;
	Channel p_out_rsp;
	Channel p_out_req;
	Channel p_in_rsp;
	
	Channel p_in_iss_req;
	Channel p_out_iss_rsp;
	
	CopiesList m_req_copies_list;
	CopiesList m_rsp_copies_list;
	
	FsmState r_fsm_state;
	FsmState r_fsm_prev_state;
	
	Request m_req;
	Request m_rsp;
	
	Request m_iss_req;
	
	
	@SuppressWarnings("unused")
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}
	

	public L1MesiController(String name, int procid, int nways, int nsets, int nwords, Channel req_to_mem, Channel rsp_from_mem, Channel req_from_mem,
			Channel rsp_to_mem, Channel req_from_iss, Channel rsp_to_iss) {
		r_procid = procid;
		r_srcid = l1StartId + procid;
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_from_mem;
		p_out_rsp = rsp_to_mem;
		p_out_req = req_to_mem;
		p_in_rsp = rsp_from_mem;
		p_in_iss_req = req_from_iss;
		p_out_iss_rsp = rsp_to_iss;
		m_cache_l1 = new CacheL1("CacheL1", procid, nways, nsets, nwords);
		p_in_req.addTgtidTranslation(r_srcid, this); // Associate the component to its srcid for the channel
		p_in_rsp.addTgtidTranslation(r_srcid, this);
		p_in_iss_req.addTgtidTranslation(r_srcid, this); // the channel index is 0 since the processor is connected to a single L1
		reset();
	}
	

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_fsm_prev_state = FsmState.FSM_IDLE;
		r_processing_req = false;
		r_outdated = false;
		r_cmd_req = cmd_t.NOP;
		r_wb_addr = 0;
		r_wb_buf = null;
		r_rsp_miss_ok = false;
		r_current_wb = false;
		m_cycle = 0;
	}
	

	void getIssRequest() {
		// This function can be called twice for the same request
		// so addToFinishedReqs can be called twice
		m_iss_req = p_in_iss_req.front(this);
		m_iss_req.setStartCycle(m_cycle); // Must be done here since proc requests can be added before simulation starts
		p_in_iss_req.addToFinishedReqs(this);
		System.out.println(m_name + " gets:\n" + m_iss_req);
	}
	

	void sendIssResponse(long addr, cmd_t type, long data) {
		p_in_iss_req.pop_front(this); // remove request from channel
		List<Long> l = new ArrayList<Long>();
		l.add(data);
		Request req;
		if (type == cmd_t.RSP_WRITE_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0); // max duration
		}
		else if (type == cmd_t.RSP_READ_WORD) {
			req = new Request(addr, r_srcid, // srcid
					r_procid, // targetid (srcid of the proc)
					type, // cmd
					m_cycle, // start cycle
					0, // max_duration
					l, // data
					0xF); // be
		}
		else {
			req = null; // avoid error
			assert (false);
		}
		
		p_out_iss_rsp.push_back(req);
	}
	

	void getRequest() {
		m_req = p_in_req.front(this);
		assert (m_req.getNwords() == 0);
		p_in_req.pop_front(this);
		System.out.println(m_name + " gets req:\n" + m_req);
	}
	

	void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.pop_front(this);
		System.out.println(m_name + " gets rsp:\n" + m_rsp);
	}
	

	void sendRequest(long addr, cmd_t type, List<Long> rdata) {
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, rdata, 0xF);
		p_out_req.push_back(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	

	void sendResponse(long addr, int tgtid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, r_srcid, tgtid, type, m_cycle, 3, rdata, 0xF);
		p_out_rsp.push_back(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}
	

	public void simulate1Cycle() {
		
		switch (r_fsm_state) {
		
		/* To complete */
		case FSM_IDLE:
			break;
		
		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)
		
		System.out.println(m_name + " next state: " + r_fsm_state);
		
		// Following code equivalent to a 1-state FSM executing in parallel
		if (!p_in_rsp.empty(this)) {
			getResponse();
			if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE || m_rsp.getCmd() == cmd_t.RSP_READ_LINE_EX || m_rsp.getCmd() == cmd_t.RSP_GETM ||
					m_rsp.getCmd() == cmd_t.RSP_GETM_LINE) {
				r_rsp_miss_ok = true;
			}
			else if (m_rsp.getCmd() == cmd_t.RSP_WRITE_LINE) {
				r_current_wb = false;
			}
			else {
				assert (false);
			}
		}
		m_cycle++;
	}
	

	public int getSrcid() {
		return r_srcid;
	}
	

	public String getName() {
		return m_name;
	}
}
