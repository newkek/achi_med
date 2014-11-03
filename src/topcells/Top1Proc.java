package topcells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import model.Channel;
import model.L1Controller;
import model.L1MesiController;
import model.MemController;
import model.MemMesiController;
import model.Module;
import model.Processor;
import model.Request;
import model.Segment;

/**
 * Topcell designed to contain one processor and one memory bank.
 * @author QLM
 *
 */
public class Top1Proc implements Topcell {

	int nb_procs = 1;
	int nb_rams = 1;
	int nways = 1;
	int nwords = 8;
	int nsets = 16;
	int cycle = 0;

	Segment mem_seg;

	Vector<Segment> seg_list = new Vector<Segment>();
	Vector<L1Controller> l1_caches;
	Vector<Processor> processors;
	Vector<MemController> mem;

	Channel iss_l1_req;
	Channel l1_iss_rsp;

	Channel l1_mem_req;
	Channel mem_l1_rsp;

	Channel mem_l1_req;
	Channel l1_mem_rsp;
	
	List<Request> finishedCacheRequests = new ArrayList<Request>();
	List<Request> finishedProcRequests = new ArrayList<Request>();

	List<Module> moduleList = new ArrayList<Module>();

	Map<Integer, Processor> processorPerSrcid = new HashMap<Integer, Processor>();
	
	public Top1Proc() {

		mem_seg = new Segment("mem_seg", 0x0, 0x1000000, true);
		seg_list.add(mem_seg);

		iss_l1_req = new Channel("iss_l1_req", 1, false, finishedProcRequests);
		l1_iss_rsp = new Channel("l1_iss_rsp", 1, false, finishedProcRequests);
		
		l1_mem_req = new Channel("l1_mem_req", nb_rams, true, finishedCacheRequests);
		mem_l1_rsp = new Channel("mem_l1_rsp", nb_procs, false, finishedCacheRequests);

		mem_l1_req = new Channel("mem_l1_req", nb_procs, false, finishedCacheRequests);
		l1_mem_rsp = new Channel("l1_mem_rsp", nb_rams, false, finishedCacheRequests);

		l1_caches = new Vector<L1Controller>(nb_procs);
		processors = new Vector<Processor>(nb_procs);
		for (int i = 0; i < nb_procs; i++) {
			L1Controller l1Ctrl = new L1MesiController("L1 controller " + i, i, nways,
					nsets, nwords, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp, iss_l1_req, l1_iss_rsp);
			l1_caches.add(l1Ctrl);
			
			
			Processor proc = new Processor("Processor " + i, i, iss_l1_req, l1_iss_rsp);
			processors.add(proc);
			processorPerSrcid.put(i, proc);
			
			moduleList.add(proc);
			moduleList.add(l1Ctrl);
		}
		
		mem = new Vector<MemController>();
		for (int i = 0; i < nb_rams; i++) {
			MemController memCtrl = new MemMesiController("Mem controller " + i,
					i, // ram_id
					nwords, seg_list, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp);
			mem.add(memCtrl);
			moduleList.add(memCtrl);
		}

		// Load requests
		/*processors.get(0).add_read(0x00400000);
		processors.get(0).add_write(0x00400000, 10);
		processors.get(0).add_write(0x00001000, 5);
		processors.get(0).add_write(0x00001004, 6);*/
	}

	public void simulate1Cycle() {
		// Simulate
		System.out.println("*** cycle " + cycle + " ***");

		for (int i = 0; i < nb_procs; i++) {
			processors.get(i).simulate1Cycle();
		}
		for (int i = 0; i < nb_procs; i++) {
			l1_caches.get(i).simulate1Cycle();
		}
		for (int i = 0; i < nb_rams; i++) {
			mem.get(i).simulate1Cycle();
		}

		iss_l1_req.simulate1Cycle();
		l1_iss_rsp.simulate1Cycle();

		l1_mem_req.simulate1Cycle();
		mem_l1_rsp.simulate1Cycle();

		mem_l1_req.simulate1Cycle();
		l1_mem_rsp.simulate1Cycle();
		
		cycle++;
	}
	
	public int getNbProcs() {
		return nb_procs;
	}
	
	public int getNbMem() {
		return nb_rams;
	}
	
	public int getNbCycles() {
		return cycle;
	}
	
	public List<Request> getFinishedCacheRequests() {
		return finishedCacheRequests;
	}
	
	public List<Request> getFinishedProcsRequests() {
		return finishedProcRequests;
	}
	
	public List<Module> getAllModules() {
		return moduleList;
	}
	
	public Processor getProcessor(int srcid) {
		return processorPerSrcid.get(srcid);
	}
}
