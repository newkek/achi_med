package topcells;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import model.Channel;
import model.L1Controller;
import model.L1MesiController;
import model.L1WtiController;
import model.MemController;
import model.MemMesiController;
import model.MemWtiController;
import model.Module;
import model.Processor;
import model.Request;
import model.Segment;

/**
 * Topcell containing two processors and one memory bank.
 * The processorPerSrcid is somehow redundant with the processors vector, but it leverages the
 * constraint of respecting index == srcid
 * @author QLM
 *
 */
public class Top2Procs implements Topcell {

	int nb_procs = 2;
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

	Vector<Channel> iss_l1_req;
	Vector<Channel> l1_iss_rsp;

	Channel l1_mem_req;
	Channel mem_l1_rsp;

	Channel mem_l1_req;
	Channel l1_mem_rsp;
	
	List<Request> finishedCacheRequests = new ArrayList<Request>();
	List<Request> finishedProcRequests = new ArrayList<Request>();

	List<Module> moduleList = new ArrayList<Module>();
	
	Map<Integer, Processor> processorPerSrcid = new HashMap<Integer, Processor>();
	
	public Top2Procs() {

		mem_seg = new Segment("mem_seg", 0x0, 0x1000000, true);
		seg_list.add(mem_seg);

		iss_l1_req = new Vector<Channel>();
		l1_iss_rsp = new Vector<Channel>();

		l1_mem_req = new Channel("l1_mem_req", nb_rams, true, finishedCacheRequests);
		mem_l1_rsp = new Channel("mem_l1_rsp", nb_procs, false, finishedCacheRequests);

		mem_l1_req = new Channel("mem_l1_req", nb_procs, false, finishedCacheRequests);
		l1_mem_rsp = new Channel("l1_mem_rsp", nb_rams, false, finishedCacheRequests);

		l1_caches = new Vector<L1Controller>(nb_procs);
		processors = new Vector<Processor>(nb_procs);
		for (int i = 0; i < nb_procs; i++) {
			Channel iss_l1 = new Channel("iss_l1_req_" + i, 1, false, finishedProcRequests);
			Channel l1_iss = new Channel("l1_iss_rsp_" + i, 1, false, finishedProcRequests);
			iss_l1_req.add(iss_l1);
			l1_iss_rsp.add(l1_iss);
			
			L1Controller l1Ctrl = new L1WtiController("L1 controller " + i, i, nways,
					nsets, nwords, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp, iss_l1, l1_iss);
			l1_caches.add(l1Ctrl);
			
			Processor proc = new Processor("Processor " + i, i, iss_l1, l1_iss);
			processors.add(proc);
			processorPerSrcid.put(i, proc); // i = srcid
		}
		
		mem = new Vector<MemController>();
		for (int i = 0; i < nb_rams; i++) {
			MemController memCtrl = new MemWtiController("Mem controller " + i, i, // ram_id
					nwords, seg_list, l1_mem_req, mem_l1_rsp, mem_l1_req,
					l1_mem_rsp);
			mem.add(memCtrl);
		}
		
		// Creating moduleList with a given order
		moduleList.add(processors.get(0));
		moduleList.add(l1_caches.get(0));
		moduleList.add(mem.get(0));
		moduleList.add(l1_caches.get(1));
		moduleList.add(processors.get(1));

		// Load requests (example)
		processors.get(0).add_write(4, 11);
		processors.get(0).add_read (4);
		/*processors.get(0).add_read (0x00400000);*/
		/*processors.get(0).add_write(0x00400000, 11);
		processors.get(0).add_read (0x00400008);
		processors.get(0).add_read (0x00400000);
		processors.get(0).add_write(0x00400008, 12);

		processors.get(0).add_read (0x00400008);
		processors.get(0).add_write(0x00400010, 13);
		processors.get(0).add_read (0x00400010);
		processors.get(0).add_write(0x00400018, 14);
		processors.get(0).add_read (0x00400018);
		*/
		
		/*processors.get(1).add_write(0x00400004, 1);*/
		processors.get(1).add_read (4);
		processors.get(1).add_write(4, 1);
		/*processors.get(1).add_write(0x0040000C, 2);
		processors.get(1).add_read (0x0040000C);
		processors.get(1).add_write(0x00400014, 3);
		processors.get(1).add_read (0x00400014);
		processors.get(1).add_write(0x0040001C, 4);
		processors.get(1).add_read (0x0040001C);
		*/
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

		// Simulate Channels last
		for (int i = 0; i < nb_procs; i++) {
			iss_l1_req.get(i).simulate1Cycle();
			l1_iss_rsp.get(i).simulate1Cycle();
		}
		
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
