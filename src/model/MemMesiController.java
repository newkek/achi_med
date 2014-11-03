package model;

import java.util.List;
import java.util.Vector;

import utils.Utile;

import model.Ram.BlockState;
import model.Request.cmd_t;

/**
 * This class implements the memory controller for the MESI protocol.
 * @author 
 * 
 */
public class MemMesiController implements MemController {

	enum FsmState {
		FSM_IDLE,
		/* To complete */
	}

	int m_srcid; // Initiator Index
	int m_words;
	int m_cycle;
	
	final static int memStartId = 100;

	String m_name;

	Ram m_ram;
	Channel p_in_req; // direct requests coming from the caches
	Channel p_out_rsp; // responses to direct requests
	Channel p_out_req; // coherence requests sent to caches
	Channel p_in_rsp; // responses to coherence requests

	CopiesList m_req_copies_list;
	CopiesList m_rsp_copies_list;

	FsmState r_fsm_state;
	boolean r_rsp_full_line;
	boolean r_write_back;
	cmd_t r_rsp_type;

	Request m_req;
	Request m_rsp;
	
	@SuppressWarnings("unused")
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}

	public MemMesiController(String name, int id, int nwords,
			Vector<Segment> seglist, Channel req_to_mem, Channel rsp_from_mem,
			Channel req_from_mem, Channel rsp_to_mem) {
		m_srcid = id + memStartId; // id is the id among the memories
		m_words = nwords;
		m_name = name;
		m_cycle = 0;
		p_in_req = req_to_mem;
		p_out_rsp = rsp_from_mem;
		p_out_req = req_from_mem;
		p_in_rsp = rsp_to_mem;
		m_ram = new Ram("Ram", id, nwords, seglist, 0);
		for (Segment seg : seglist) {
			seg.setTgtid(m_srcid);
		}
		p_in_req.addAddrTranslation(seglist, this);
		p_in_rsp.addTgtidTranslation(m_srcid, this);
		reset();
	}

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_rsp_full_line = false;
		r_rsp_type = cmd_t.NOP;
		r_write_back = false;
		m_cycle = 0;
	}

	void getRequest() {
		m_req = p_in_req.front(this);
		p_in_req.pop_front(this);
		System.out.println(m_name + " receives req:\n" + m_req);
	}

	void getResponse() {
		m_rsp = p_in_rsp.front(this);
		p_in_rsp.pop_front(this);
		System.out.println(m_name + " receives rsp:\n" + m_rsp);
	}

	void sendRequest(long addr, int targetid, cmd_t type) {
		Request req = new Request(addr, m_srcid, targetid, type, m_cycle, 3);
		p_out_req.push_back(req);
		System.out.println(m_name + " sends req:\n" + req);
	}

	void sendResponse(long addr, int targetid, cmd_t type, List<Long> rdata) {
		Request rsp = new Request(addr, m_srcid, targetid, type, m_cycle, 3, // max_duration
				rdata, 0xF);
		p_out_rsp.push_back(rsp);
		System.out.println(m_name + " sends rsp:\n" + rsp);
	}

	public void simulate1Cycle() {

		switch (r_fsm_state) {

		case FSM_IDLE:
			break;

		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)
		System.out.println(m_name + " next state: " + r_fsm_state);

		m_cycle++;
	}
	
	public int getSrcid() {
		return m_srcid;
	}
	
	public String getName() {
		return m_name;
	}

}
