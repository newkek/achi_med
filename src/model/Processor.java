package model;

import java.util.ArrayList;
import java.util.List;

import model.Request.cmd_t;

/**
 * This class implements a basic ISS only capable of issuing reads and writes
 * @author QLM
 *
 */
public class Processor implements Module {

	int m_id;
	String m_name;

	int r_nb_req;
	int r_nb_rsp;
	int r_cycle = 0;

	Channel p_out_req;
	Channel p_in_rsp;

	public Processor(String name, int id, Channel req_from_iss, Channel rsp_to_iss) {
		m_name = name;
		m_id = id; // for a processor, the srcid is equal to the id
		p_out_req = req_from_iss;
		p_in_rsp = rsp_to_iss;
		r_nb_req = 0;
		r_nb_rsp = 0;
		p_in_rsp.addTgtidTranslation(m_id, this); // Translation r_srcid (real unique srcid) to channel index
	}

	public void add_read(long addr) {
		Request req = new Request(addr, m_id, m_id + L1MesiController.l1StartId, cmd_t.READ_WORD, r_cycle, 0, null,	0xF);
		p_out_req.push_back(req);
		r_nb_req++;
	}

	public void add_write(long addr, long data) {
		List<Long> l = new ArrayList<Long>();
		l.add(data);
		Request req = new Request(addr, m_id, m_id + L1MesiController.l1StartId, cmd_t.WRITE_WORD, r_cycle, 0, l, 0xF);
		p_out_req.push_back(req);
		r_nb_req++;
	}

	void add_nop() {
		Request req = new Request(0, m_id, m_id + L1MesiController.l1StartId, cmd_t.NOP, r_cycle, 0, null, 0xF);
		p_out_req.push_back(req);
		r_nb_req++;
	}

	public void simulate1Cycle() {
		if (!p_in_rsp.empty(this)) {
			Request r = p_in_rsp.front(this);
			System.out.println(m_name + " received response: \n" + r);
			p_in_rsp.pop_front(this);
			r_nb_rsp++;
		}
		r_cycle++;
	}

	
	public boolean stop_ok() {
		return p_out_req.empty(this) && r_nb_rsp == r_nb_req;
	}
	
	
	public int getSrcid() {
		return m_id;
	}

	public String getName() {
		return m_name;
	}

}
