/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
**This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
**To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.planner.TrajectoryPlanner;
import ab.utils.ABUtil;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.ABType;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class SeaAgent implements Runnable {
	private ActionRobot aRobot;
	private Random randomGenerator;
	public int currentLevel = 1;
	public static int time_limit = 12;
	private Map<Integer,Integer> scores = new LinkedHashMap<Integer,Integer>();
	TrajectoryPlanner tp;
	private boolean firstShot;
	private Point prevTarget;
	private ABType bird;

	private List<ABObject> allObjects;
	private List<ABObject> sortedHorizontalObjects;
	private List<ABObject> sortedVerticalObjects;

	// a standalone implementation of the Naive Agent
	public SeaAgent() {
		Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        System.out.println(sdf.format(cal.getTime()) );
		aRobot = new ActionRobot();
		tp = new TrajectoryPlanner();
		prevTarget = null;
		firstShot = true;
		randomGenerator = new Random();
		// --- go to the Poached Eggs episode level selection page ---
		ActionRobot.GoFromMainMenuToLevelSelection();

	}

	// run the client
	@Override
	public void run() {

		aRobot.loadLevel(currentLevel);
		while (true) {
			GameState state = solve();
			if (state == GameState.WON) {
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				int score = StateUtil.getScore(ActionRobot.proxy);
				if(!scores.containsKey(currentLevel))
					scores.put(currentLevel, score);
				else
				{
					if(scores.get(currentLevel) < score)
						scores.put(currentLevel, score);
				}
				int totalScore = 0;
				for(Integer key: scores.keySet()){
					totalScore += scores.get(key);
					System.out.println(" Level " + key
							+ " Score: " + scores.get(key) + " ");
				}
				System.out.println("Total Score: " + totalScore);
				aRobot.loadLevel(++currentLevel);
				if(currentLevel == 22) {
					Calendar cal = Calendar.getInstance();
			        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
			        System.out.println(sdf.format(cal.getTime()) );
					System.exit(0);
				}
				// make a new trajectory planner whenever a new level is entered
				tp = new TrajectoryPlanner();

				// first shot on this level, try high shot first
				firstShot = true;
			} else if (state == GameState.LOST) {
				System.out.println("Restart");
				aRobot.restartLevel();
			} else if (state == GameState.LEVEL_SELECTION) {
				System.out
				.println("Unexpected level selection page, go to the last current level : "
						+ currentLevel);
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.MAIN_MENU) {
				System.out
				.println("Unexpected main menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			} else if (state == GameState.EPISODE_MENU) {
				System.out
				.println("Unexpected episode menu page, go to the last current level : "
						+ currentLevel);
				ActionRobot.GoFromMainMenuToLevelSelection();
				aRobot.loadLevel(currentLevel);
			}

		}

	}

	private double distance(Point p1, Point p2) {
		return Math
				.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
						* (p1.y - p2.y));
	}

	public GameState solve()
	{
		// capture Image
		BufferedImage screenshot = ActionRobot.doScreenShot();

		// process image
		Vision vision = new Vision(screenshot);

		// find the slingshot
		Rectangle sling = vision.findSlingshotMBR();

		// confirm the slingshot
		while (sling == null && aRobot.getState() == GameState.PLAYING) {
			System.out
			.println("No slingshot detected. Please remove pop up or zoom out");
			ActionRobot.fullyZoomOut();
			screenshot = ActionRobot.doScreenShot();
			vision = new Vision(screenshot);
			sling = vision.findSlingshotMBR();
		}
        // get all the pigs
 		List<ABObject> pigs = vision.findPigsMBR();

 		// get all blocks
 		allObjects = vision.findBlocksMBR();
 		sortHorizontal();
 		sortVertical();

		GameState state = aRobot.getState();

		pigs = sortObjectsHorizontal(pigs);

		// if there is a sling, then play, otherwise just skip.
		if (sling != null) {
			if (!pigs.isEmpty()) {
				bird = aRobot.getBirdTypeOnSling();
				Point releasePoint = null;
				Shot shot = new Shot();
				int dx,dy;
				{
					// random pick up a pig
					ABObject pig = pigs.get(0);
					ABObject targetChosen = getMostSupportingStructure(vision, pig);

					if (targetChosen == null) {
						allObjects = sortObjectsHorizontal(allObjects);
						allObjects = removeUnreachable(vision, allObjects, sling);

						targetChosen = bestStructure(vision, pig, sling);
						if (targetChosen != null) {

						}
						if (targetChosen == null) {
							targetChosen = pig;

						}
					}
					Point _tpt = targetChosen.getLocation();

					// if the target is very close to before, randomly choose a
					// point near it
					if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
						double _angle = randomGenerator.nextDouble() * Math.PI * 2;
						_tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
						_tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
						System.out.println("Randomly changing to " + _tpt);
					}

					prevTarget = new Point(_tpt.x, _tpt.y);

					// estimate the trajectory
					ArrayList<Point> pts = tp.estimateLaunchPoint(sling, _tpt);

					if (pts.size() == 1)
						releasePoint = pts.get(0);
					else if (pts.size() == 2)
					{
						// randomly choose between the trajectories, with a 1 in
						// 10 chance of choosing the high one
						if (randomGenerator.nextInt(10) == 0)
							releasePoint = pts.get(1);
						else
							releasePoint = pts.get(0);
					}
					else
						if(pts.isEmpty())
						{
							System.out.println("No release point found for the target");
							System.out.println("Try a shot with 45 degree");
							releasePoint = tp.findReleasePoint(sling, Math.PI/4);
						}

					// Get the reference point
					Point refPoint = tp.getReferencePoint(sling);

					//Calculate the tapping time according the bird type
					if (releasePoint != null) {
						double releaseAngle = tp.getReleaseAngle(sling,
								releasePoint);
						//System.out.println("Release Point: " + releasePoint);
						//System.out.println("Release Angle: "
						//		+ Math.toDegrees(releaseAngle));
						int tapInterval = 0;
						switch (bird)
						{
							case RedBird:
								tapInterval = 0; break;               // start of trajectory
							case YellowBird:
								tapInterval = 70 + randomGenerator.nextInt(20);break; // 65-90% of the way
							case WhiteBird:
								tapInterval =  70 + randomGenerator.nextInt(15);break; // 70-90% of the way
							case BlackBird:
								tapInterval =  100 + randomGenerator.nextInt(15);break; // 70-90% of the way
							case BlueBird:
								tapInterval =  75 + randomGenerator.nextInt(15);break; // 65-85% of the way
							default:
								tapInterval =  60;
						}
						int tapTime = tp.getTapTime(sling, releasePoint, _tpt, tapInterval);
						dx = (int)releasePoint.getX() - refPoint.x;
						dy = (int)releasePoint.getY() - refPoint.y;
						shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
					}
					else
						{
							System.err.println("No Release Point Found");
							return state;
						}
				}

				// check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
				{
					ActionRobot.fullyZoomOut();
					screenshot = ActionRobot.doScreenShot();
					vision = new Vision(screenshot);
					Rectangle _sling = vision.findSlingshotMBR();
					if(_sling != null)
					{
						double scale_diff = Math.pow((sling.width - _sling.width),2) +  Math.pow((sling.height - _sling.height),2);
						if(scale_diff < 25)
						{
							if(dx < 0)
							{
								aRobot.cshoot(shot);
								state = aRobot.getState();
								if ( state == GameState.PLAYING )
								{
									screenshot = ActionRobot.doScreenShot();
									vision = new Vision(screenshot);
									List<Point> traj = vision.findTrajPoints();
									tp.adjustTrajectory(traj, sling, releasePoint);
									firstShot = false;
								}
							}
						}
						else
							System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
					}
					else
						System.out.println("no sling detected, can not execute the shot, will re-segement the image");
				}
			}
		}
		return state;
	}

	public static void main(String args[]) {
		SeaAgent na = new SeaAgent();
		if (args.length > 0)
			na.currentLevel = Integer.parseInt(args[0]);
		na.run();
	}

	public ABObject getMostSupportingStructure (Vision v, ABObject pig) {
		HashSet<ABObject> check = new HashSet<ABObject> ();
		List<ABObject> allObjects = v.findBlocksMBR();
		LinkedList<ABObject> queue = new LinkedList<ABObject> ();
		ArrayList<ABObject> supporters = new ArrayList<ABObject> ();
		queue.add(pig);
		check.add(pig);
		Rectangle sling = v.findSlingshotMBR();

		while(!queue.isEmpty()) {
			ABObject o = queue.poll();
			ABObject object = o;
			Point p = object.getLocation();

			ArrayList<Point> pts = tp.estimateLaunchPoint(sling, p);

			for (int jj = 0; jj < pts.size(); jj++) {
				Point refPoint = tp.getReferencePoint(sling);
				Point releasePoint = pts.get(jj);
				int dx, dy;
				dx = (int)releasePoint.getX() - refPoint.x;
				dy = (int)releasePoint.getY() - refPoint.y;
				Shot shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, 0);
				if (ABUtil.isReachable(v, p, shot)) {
					double comp = compatibility(object);
					if(comp > 0.6) {
						return object;
					}
				}
			}

			List<ABObject> currentSupporters = ABUtil.getSupporters(o, allObjects);
			for (ABObject supporter : currentSupporters) {
				if(!check.contains(supporter)) {
					queue.add(supporter);
					check.add(supporter);
					supporters.add(supporter);
				}
			}
		}
		return null;
	}

	public void sortHorizontal () {
		sortedHorizontalObjects = new ArrayList<ABObject> (allObjects);
		Collections.sort(sortedHorizontalObjects,new Comparator<ABObject>() {
		    @Override
		    public int compare(ABObject a, ABObject b) {
		        return (int) a.getCenterX() - (int) b.getCenterX();
		    }
		});
	}

	public void sortVertical () {
		sortedVerticalObjects = new ArrayList<ABObject> (allObjects);
		Collections.sort(sortedVerticalObjects,new Comparator<ABObject>() {
		    @Override
		    public int compare(ABObject a, ABObject b) {
		        return (int) a.getCenterY() - (int) b.getCenterY();
		    }
		});
	}

	public List<ABObject> sortObjectsHorizontal (List<ABObject> pigs) {
		List<ABObject> sortedPigs = new ArrayList<ABObject>(pigs);
		Collections.sort(sortedPigs,new Comparator<ABObject>() {
		    @Override
		    public int compare(ABObject a, ABObject b) {
		        return (int) a.getCenterX() - (int) b.getCenterX();
		    }
		});
		return sortedPigs;
	}

	public double compatibility (ABObject structure) {
		double value = 0;
		if (structure.type.equals(ABType.Stone)){
			switch (bird)
			{
				case BlackBird:
					value = 0.9; break;
				default:
					value = 0.1; break;
			}
		} else if (structure.type.equals(ABType.Wood)) {
			switch (bird)
			{
				case YellowBird:
					value = 1.0; break;
				case BlackBird:
					value =  1.0; break;
				case WhiteBird:
					value =  0.5; break;
				case BlueBird:
					value =  0.2; break;
				default:
					value =  0.65; break;
			}
		} else if (structure.type.equals(ABType.Ice)) {
			switch (bird)
			{
				case YellowBird:
					value = 0.3; break;
				case BlackBird:
					value =  1.0; break;
				case WhiteBird:
					value =  0.6; break;
				case BlueBird:
					value =  1.0; break;
				default:
					value =  0.65; break;
			}
		} else if (structure.type.equals(ABType.TNT)) {
			value = 1.0;
		} else if (structure.type.equals(ABType.Unknown)) {
			value = 0.7;
		} else if (structure.type.equals(ABType.Pig)) {
			value = 0.7;
		}
		return value;
	}

	public ABObject bestStructure (Vision v, ABObject pig, Rectangle sling) {
		List<ABObject> allObjects = v.findBlocksMBR();
		List<ABObject> outerStructures = sortObjectsHorizontal(allObjects);
		outerStructures = removeUnreachable(v, allObjects, sling);
		if (outerStructures.size() < 1) {
			return null;
		}

		String[] criteria = {"Breakability", "Perimeter", "Distance", "Height"};
		// Pairwise Matrix
		double[][] pairwise_matrix = {{1.0, 0.0, 0.0, 0.0},
									  {0.8, 1.0, 0.0, 0.0},
									  {2.5, 4.0, 1.0, 0.0},
									  {0.7, 0.7, 0.1, 1.0}};

		// Complete Table
		for (int ii = 0; ii < 4; ii++) {
			for (int jj = ii + 1; jj < 4; jj++) {
				pairwise_matrix[ii][jj] = 1.0/pairwise_matrix[jj][ii];
			}
		}

		String[] alternatives = new String[outerStructures.size()];
		for (int ii = 0; ii < outerStructures.size(); ii++) {
			alternatives[ii] = outerStructures.get(ii).toString() + " " + outerStructures.get(ii).type;
		}

		double[][] alt_matrix = new double[alternatives.length][4];

		//// Breakability
		double[] breakability = new double[alternatives.length];
		double sum = 0;
		for (int ii = 0; ii < breakability.length; ii++) {
			breakability[ii] = compatibility(outerStructures.get(ii));
			sum = breakability[ii] + sum;
		}
		// Normalize
		for (int ii = 0; ii < breakability.length; ii++) {
			breakability[ii] = breakability[ii] / sum;
			alt_matrix[ii][0] = breakability[ii];
		}

		sum = 0;
		//// Perimeter
		double[] perimeter = new double[alternatives.length];
		for (int ii = 0; ii < perimeter.length; ii++) {
			double counter = 0;
			for (int jj = 0; jj < allObjects.size(); jj++) {
				if(distanceStructures(outerStructures.get(ii), allObjects.get(jj)) < 30) {
					counter++;
				}
			}
			perimeter[ii] = counter;
			sum = perimeter[ii] + sum;
		}

		// Normalize
		for (int ii = 0; ii < perimeter.length; ii++) {
			perimeter[ii] = perimeter[ii] / sum;
			alt_matrix[ii][1] = perimeter[ii];
		}

		sum = 0;
		//// Distance to pig
		double[] pigDistances = new double[alternatives.length];
		for (int ii = 0; ii < pigDistances.length; ii++) {
			pigDistances[ii] = distanceStructures(outerStructures.get(ii), pig);
			double xRelative = outerStructures.get(ii).getLocation().getX() - pig.getLocation().getX();
			double yRelative = outerStructures.get(ii).getLocation().getY() - pig.getLocation().getY();

			if(xRelative < 0 && xRelative > -30) {
					pigDistances[ii] = pigDistances[ii] * 3;
			}

			if(outerStructures.get(ii).getLocation().getX() > pig.getLocation().getX()) {
				pigDistances[ii] = pigDistances[ii] * 0.1;
			}
			if(outerStructures.get(ii).getLocation().getY() > pig.getLocation().getY()) {
				pigDistances[ii] = pigDistances[ii] * 0.1;
			}
			sum = pigDistances[ii] + sum;
		}

		// Normalize
		for (int ii = 0; ii < pigDistances.length; ii++) {
			pigDistances[ii] = pigDistances[ii] / sum;
			alt_matrix[ii][2] = pigDistances[ii];
		}

		sum = 0;
		//// Height
		double[] heights = new double[alternatives.length];
		for (int ii = 0; ii < heights.length; ii++) {
			heights[ii] = outerStructures.get(ii).getCenterY();
			sum = heights[ii] + sum;
		}

		// Normalize
		for (int ii = 0; ii < heights.length; ii++) {
			heights[ii] = heights[ii] / sum;
			alt_matrix[ii][3] = heights[ii];
		}

		AHP ahp = new AHP(criteria, pairwise_matrix, alternatives, alt_matrix);
		double[] result = ahp.compute();
		double best = Double.MIN_VALUE;
		int bestIndex = -1;
		for (int ii = 0; ii < result.length; ii++) {
			if (result[ii] > best) {
				best = result[ii];
				bestIndex = ii;
			}
		}
		return outerStructures.get(bestIndex);
	}

	public List<ABObject> removeUnreachable (Vision v, List<ABObject> structures, Rectangle sling) {
		ArrayList<ABObject> filtered = new ArrayList<ABObject> ();
		structures = sortObjectsHorizontal(structures);
		for (ABObject s : structures) {
			Point p = s.getLocation();
			ArrayList<Point> pts = tp.estimateLaunchPoint(sling, p);
			boolean reachable = false;
			for (int jj = 0; jj < pts.size(); jj++) {
				Point refPoint = tp.getReferencePoint(sling);
				Point releasePoint = pts.get(jj);
				int dx, dy;
				dx = (int)releasePoint.getX() - refPoint.x;
				dy = (int)releasePoint.getY() - refPoint.y;
				Shot shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, 0);

				if (ABUtil.isReachable(v, p, shot)) {
					reachable = true;
				}
			}

			if (reachable) {
				filtered.add(s);
			}
		}
		return filtered;
	}

	public double distanceStructures(ABObject structure1, ABObject structure2) {
		double xDistance = structure1.getCenterX() - structure2.getCenterX();
		double yDistance = structure1.getCenterY() - structure2.getCenterY();
		double distance = (Math.sqrt(Math.sqrt((xDistance * xDistance) + (yDistance * yDistance))));
		return 30.0/distance;
	}
}
