package simulation;

import java.awt.Dimension;

import controller.Controlleur;
import topcells.*;
import view.Vue;

/**
 * Main class.
 * @author QLM
 *
 */
public class Simul {
	public static void main(String[] args) {
		Dimension dim = new Dimension(1000, 800);
		Top2Procs topcell = new Top2Procs();
		Vue vue = new Vue(dim);
		new Controlleur(vue, topcell);

		vue.setVisible(true);
		vue.pack();
	}
}
