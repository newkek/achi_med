package model;

import java.util.List;
import java.util.Vector;

import utils.Utile;
import model.Request.cmd_t;
import java.util.ArrayList;
/**
 * This class implements the memory controller for the WTI protocol.
 * @author 
 */
public class MemWtiController implements MemController {

	enum FsmState {
		FSM_IDLE,
		FSM_INVALL,
		FSM_INVWAIT,
		FSM_INVWR,
		FSM_WRITE_RAM
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

	Request m_req;
	Request m_rsp;
	
	
	@SuppressWarnings("unused")
	private long align(long addr) {
		return (addr & ~((1 << (2 + Utile.log2(m_words))) - 1));
	}

	public MemWtiController(String name, int id, int nwords,
			Vector<Segment> seglist, Channel req_to_mem, Channel rsp_from_mem,
			Channel req_from_mem, Channel rsp_to_mem) {
		m_srcid = id + memStartId; // Id for srcid
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
		
		/* To complete */
		case FSM_IDLE:
			if(!p_in_req.empty(this)){
				getRequest();
				if(m_req.getCmd() == cmd_t.WRITE_LINE){
					r_fsm_state = FsmState.FSM_INVALL;
				}
				else if(m_req.getCmd() == cmd_t.READ_LINE){
					///!\ mettre a jour la liste des copies
					m_ram.addCopy(m_req.getAddress(), m_req.getSrcid());
					List<Long> rdata = new ArrayList<Long>();
					m_ram.getLine(m_req.getAddress(), rdata);
					sendResponse(m_req.getAddress(), m_req.getSrcid(), cmd_t.RSP_READ_LINE, rdata);
				}
			}
			break;
		
			
		case FSM_INVALL:
			int i = 0, nbCopies, tgtid = 0;
			nbCopies = m_ram.nbCopies(m_req.getAddress());
			CopiesList cp = m_ram.copies(m_req.getAddress());
			while(i<nbCopies){
				tgtid = cp.getNextOwner();
				sendRequest(m_req.getAddress(), tgtid, cmd_t.INVAL);				
				i++;
			}
			if(i!=0){
				r_fsm_state = FsmState.FSM_INVWAIT;				
			}
			else{
				r_fsm_state = FsmState.FSM_INVWR;
			}
			break;
		
		case FSM_INVWAIT:
			if(!p_in_req.empty(this)){
				getResponse();
				if(m_ram.nbCopies(m_rsp.getAddress()) == 0){
					r_fsm_state = FsmState.FSM_INVWR;
				}
				else{
					m_ram.removeCopy(m_rsp.getAddress(), m_rsp.getSrcid());
				}	
			}
			
				
		case FSM_INVWR:
			m_ram.write(m_req.getAddress(), m_req.getData().get(0), m_req.getBe());
			/*List<Long> rdata = new ArrayList<Long>();
			m_ram.read(m_req.getAddress(), rdata);*/

			r_fsm_state = FsmState.FSM_IDLE;
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
