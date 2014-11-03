package model;

import java.util.ArrayList;
import java.util.List;

import utils.Utile;
import model.LineState.cacheSlotState;
import model.Request.cmd_t;

/**
 * This class implements a L1 WTI controller. The l1StartId purpose is to make a correspondence between the cache number (r_id), ranging from 0 to nb_caches -
 * 1, and the srcid on the network.
 * @author
 */
public class L1WtiController implements L1Controller {
	
	// Shouldn't be changed: a small value may create conflicts with processors srcid
	// a value giving srcid higher than 31 will not work
	// Collision must also be avoided with the Ram srcid
	// (A better implementation is possible)
	static final int l1StartId = 10;
	
	
	enum FsmState {
		FSM_IDLE,
		FSM_WRITE_HIT,
		FSM_WRITE_REQ,
		FSM_READ_MISS,
		FSM_UPD_CACHE,
		FSM_MISS_WAIT,
		FSM_WAIT
		/* To complete */
	}
	
	int r_srcid; // Global initiator and target index
	int r_procid; // srcid of the associated processor
	
	boolean r_outdated;
	cmd_t r_cmd_req;
	long r_wb_addr;
	List<Long> r_wb_buf;
	boolean r_rsp_miss_ok;
	boolean r_update_cache;
	
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
	
	FsmState r_fsm_state = FsmState.FSM_IDLE;;
	FsmState r_fsm_prev_state;
	
	Request m_req;
	Request m_rsp;
	
	Request m_iss_req;
	
	@SuppressWarnings("unused")
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}

	public L1WtiController(String name, int procid, int nways, int nsets, int nwords, Channel req_to_mem, Channel rsp_from_mem, Channel req_from_mem,
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
		p_in_req.addTgtidTranslation(r_srcid, this); // Translation r_srcid (real unique srcid) to channel index
		p_in_rsp.addTgtidTranslation(r_srcid, this);
		p_in_iss_req.addTgtidTranslation(r_srcid, this);
		reset();
	}
	

	void reset() {
		r_fsm_state = FsmState.FSM_IDLE;
		r_fsm_prev_state = FsmState.FSM_IDLE;
		r_outdated = false;
		r_cmd_req = cmd_t.NOP;
		r_wb_addr = 0;
		r_wb_buf = null;
		r_rsp_miss_ok = false;
		m_cycle = 0;
	}
	

	void getIssRequest() {
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
		m_iss_req = null;
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
	

	void sendRequest(long addr, cmd_t type, List<Long> data) {
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, data, 0xF);
		p_out_req.push_back(req);
		System.out.println(m_name + " sends req:\n" + req);
	}
	
	void sendRequest(long addr, cmd_t type, Long wdata, int be) {
		List<Long> data = new ArrayList<Long>();
		data.add(wdata);
		Request req = new Request(addr, r_srcid, -1, type, m_cycle, 3, data, be);
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
		
		/* To Complete */
		case FSM_IDLE:
			System.out.println("begin switch");
			if(!p_in_iss_req.empty(this)){
				getIssRequest();
				System.out.println("ever goes here");
				if(m_iss_req.getCmd() == cmd_t.WRITE_WORD){//Requete du (d'un) processeur
					LineState req_state = new LineState();
					List<Long> returned_dt = new ArrayList<Long>();
					
					m_cache_l1.read(m_iss_req.getAddress(), returned_dt, req_state);
					if(req_state.state == cacheSlotState.VALID){
						r_fsm_state = FsmState.FSM_WRITE_HIT;
					}
					else{
						r_fsm_state = FsmState.FSM_WRITE_REQ;
					}
				}
				else if(m_iss_req.getCmd() == cmd_t.READ_WORD){
					LineState req_state = new LineState();
					List<Long> returned_dt = new ArrayList<Long>();
					m_cache_l1.read(m_iss_req.getAddress(), returned_dt, req_state);
					System.out.println("               \t\t\t   returned data"+returned_dt);
					if(req_state.state == cacheSlotState.EMPTY){
						r_fsm_state = FsmState.FSM_READ_MISS;
					}
					else{
						//m_cache_l1.set_line(m_req.getAddress(), returned_dt, true);
						//send response with data to proc
						System.out.println("never goes in this thing");
						System.out.println(returned_dt);
						sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_READ_WORD, returned_dt.get(0));
						r_fsm_state = FsmState.FSM_IDLE;
					}
				}
				else if(m_iss_req.getCmd() == cmd_t.INVAL){
					//Invalider la ligne de cache
					m_cache_l1.inval(m_req.getAddress(), true);
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_INVAL_CLEAN, null);
					r_fsm_state = FsmState.FSM_IDLE;
				}
			}
			break;
		
		case FSM_WRITE_HIT:
			m_cache_l1.write(m_iss_req.getAddress(), m_iss_req.getData().get(0));
			r_fsm_state = FsmState.FSM_WRITE_REQ;
			break;
			
		case FSM_WRITE_REQ:
			sendRequest(m_iss_req.getAddress(), cmd_t.WRITE_LINE, m_iss_req.getData());
			sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_WRITE_WORD, 0);
			r_fsm_state = FsmState.FSM_IDLE;
			break;
			
		case FSM_READ_MISS:
			sendRequest(m_iss_req.getAddress(), cmd_t.READ_LINE, m_iss_req.getData());
			r_fsm_state = FsmState.FSM_WAIT;
			break;
			
		case FSM_WAIT:
			if(!p_in_rsp.empty(this)){
				getResponse();
				if(m_rsp.getCmd() == cmd_t.RSP_READ_LINE){
					m_cache_l1.set_line(m_iss_req.getAddress(), m_rsp.getData(), false);
					LineState req_state = new LineState();
					List<Long> returned_dt = new ArrayList<Long>();
					
					m_cache_l1.read(m_iss_req.getAddress(), returned_dt, req_state);
					sendIssResponse(m_iss_req.getAddress(), cmd_t.RSP_READ_WORD,returned_dt.get(0));
					System.out.println("DATA RECUE : "+m_rsp.getData());
					r_fsm_state = FsmState.FSM_IDLE;
				}
			}
			break;
		
		default:
			assert (false);
			break;
		} // end switch(r_fsm_state)
		
		System.out.println(m_name + " next state: " + r_fsm_state);
		
		// Following code equivalent to a 1-state FSM executing in parallel
		/*if (!p_in_rsp.empty(this)) {
			getResponse();
			if (m_rsp.getCmd() == cmd_t.RSP_READ_LINE) {
				r_rsp_miss_ok = true;
			}
			else if (m_rsp.getCmd() == cmd_t.RSP_WRITE_WORD) {
				// ...
			}
			else {
				assert (false);
			}
		}*/
		m_cycle++;
	}
	

	public int getSrcid() {
		return r_srcid;
	}
	

	public String getName() {
		return m_name;
	}
}
