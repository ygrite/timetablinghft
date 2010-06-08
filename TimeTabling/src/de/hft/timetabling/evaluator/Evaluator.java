package de.hft.timetabling.evaluator;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.hft.timetabling.common.ICourse;
import de.hft.timetabling.common.ICurriculum;
import de.hft.timetabling.common.IProblemInstance;
import de.hft.timetabling.common.IRoom;
import de.hft.timetabling.common.ISolution;
import de.hft.timetabling.services.IEvaluatorService;
import de.hft.timetabling.services.ISolutionTableService;
import de.hft.timetabling.services.ServiceLocator;

public class Evaluator implements IEvaluatorService {

	/**
	 * Evaluate the soft constrains for each curriculum and store the penalty
	 * points
	 * 
	 * @author Roy
	 */

	public Evaluator() {
		// constructor
	}

	private IProblemInstance currentInstance;
	private ICourse[][] currentCode;
	private IRoom currentRoom;
	private ISolutionTableService solutionTable;

	// private ICourse currentCourseDetails;

	/**
	 * This method calculates the penalty in Room Capacity. Needs to be based on
	 * curriculum to have separate penalty points for each
	 * 
	 * @param solution
	 *            A solution instance is send for evaluation
	 * @return iCost Returns the penalty value
	 */
	private int costsOnRoomCapacity(ISolution solution, ICurriculum curriculum) {
		int iCost = 0;
		int iNoOfStudents, iRoomCapacity;
		int r, p;
		Set<ICourse> courses;

		// Get the problem instance to get the values related to it
		currentInstance = solution.getProblemInstance();
		// Get the solution array for this ISolution
		currentCode = solution.getCoding();

		// Get the courses for the curriculum
		courses = curriculum.getCourses();

		for (p = 0; p < currentInstance.getNumberOfPeriods(); p++) {
			for (r = 0; r < currentInstance.getNumberOfRooms(); r++) {
				// Assuming the value is null if no course is assigned
				// the course should be contained in the curriculum
				if ((currentCode[p][r] != null)
						&& courses.contains(currentCode[p][r])) {
					iNoOfStudents = currentCode[p][r].getNumberOfStudents();

					currentRoom = currentInstance.getRoomByUniqueNumber(r);
					iRoomCapacity = currentRoom.getCapacity();

					// Each student above the capacity counts as 1 point of
					// penalty
					if (iNoOfStudents > iRoomCapacity) {
						iCost += (iNoOfStudents - iRoomCapacity);
					}

					// There are no more courses in the same period but
					// different room
					break;
				}
			}
		}

		return iCost;
	}

	/**
	 * Method to calculate the penalty on min working days Need one more
	 * parameter to make it curriculum specific
	 * 
	 * @param solution
	 *            A solution instance is send for evaluation
	 * @return iCost Returns the penalty value
	 */
	private int costsOnMinWorkingDays(ISolution solution, ICurriculum curriculum) {
		int iCost = 0;
		int p, r, iWorkingDays;
		int iMinWorkingDays, iPeriodPerDay;
		Set<ICourse> courses;
		// String ArrayCourse[];
		ICourse Course;
		Iterator<ICourse> it;

		currentInstance = solution.getProblemInstance();
		currentCode = solution.getCoding();
		courses = curriculum.getCourses();

		iPeriodPerDay = currentInstance.getPeriodsPerDay();

		// To convert set to array
		// String[] array = courses.toArray(new String[courses.size()]);

		// Use iterator to parse through set
		it = courses.iterator();
		while (it.hasNext()) {
			Course = it.next();
			iMinWorkingDays = Course.getMinWorkingDays();
			iWorkingDays = 0;
			boolean bDay = true;
			for (p = 0; p < currentInstance.getNumberOfPeriods(); p++) {
				// the course should count only once per day
				if (p % iPeriodPerDay == 0) {
					bDay = true;
				}
				// improve performance: jump to the next day
				// continue with loop, subtract 1 as loop increments p value by
				// 1 resulting the flag to true at next loop
				else if (bDay == false) {
					p += (iPeriodPerDay - (p % iPeriodPerDay)) - 1;
					// bDay = true;
					continue;
				}

				for (r = 0; r < currentInstance.getNumberOfRooms(); r++) {
					// Assuming the value is null if no course is assigned
					// the course should be equal to the course
					if ((currentCode[p][r] != null)
							&& (currentCode[p][r] == Course)) {

						// Check the flag for day
						if (bDay) {
							iWorkingDays++;
							bDay = false;
						}
						// There are no more courses in the same period but
						// different room
						break;
					}
				}
			}
			// Each day below the minimum counts as 5 points of penalty
			if (iWorkingDays < iMinWorkingDays) {
				iCost += (iMinWorkingDays - iWorkingDays) * 5;
			}

		}
		return iCost;
	}

	/**
	 * Method to calculate the penalty on curriculum compactness and room
	 * stability Need one more parameter to make it curriculum specific
	 * 
	 * @param solution
	 *            A solution instance is send for evaluation
	 * @return iCost Returns the penalty value of both soft constrains
	 */
	private int costsOnCurriculumCompactnessAndRoomStability(
			ISolution solution, ICurriculum curriculum) {
		int iCost = 0;
		int p, r, d, iPreviousRoom;
		int iPreviousPeriod;
		Set<ICourse> courses;

		currentInstance = solution.getProblemInstance();
		currentCode = solution.getCoding();
		courses = curriculum.getCourses();

		// Initial value of day and period
		d = 1;
		p = 0;
		// Make the looping for each day
		while (d < currentInstance.getNumberOfDays()) {
			iPreviousPeriod = -1;
			iPreviousRoom = -1;
			for (; p < currentInstance.getPeriodsPerDay() * d; p++) {
				for (r = 0; r < currentInstance.getNumberOfRooms(); r++) {
					// Assuming the value is null if no course is assigned
					// the course should be contained in the curriculum
					if ((currentCode[p][r] != null)
							&& courses.contains(currentCode[p][r])) {
						if ((iPreviousPeriod == -1) && (iPreviousRoom == -1)) {
							iPreviousPeriod = p;
							iPreviousRoom = r;
							// there should not be another course of same
							// curriculum in different room in the same period
							// it should also not go with next if statement in
							// first run
							break;
						}

						// Check if not new day
						// if (p % currentInstance.getPeriodsPerDay()!= 0) {
						if ((iPreviousPeriod != -1) && (iPreviousRoom != -1)) {
							// Check if the previous period course is of the
							// same curriculum
							// if (!courses.contains(currentCode[p -
							// 1][iPreviousRoom]))
							// {
							if (iPreviousPeriod != (p - 1)) {
								iCost += 2;
							}
							// Penalty for Room
							// currentRoom =
							// currentInstance.getRoomByUniqueNumber(r);
							// previousRoom =
							// currentInstance.getRoomByUniqueNumber(iPreviousRoom);
							// if (currentRoom.getId() != previousRoom.getId())
							// {
							if (r != iPreviousRoom) {
								iCost++;
							}
							// Assign the new previous Room and Period values
							iPreviousRoom = r;
							iPreviousPeriod = p;
						}
						// there should not be another course of same
						// curriculum in different room in the same period
						break;
					}
				}

			}
			// Increment to next day
			d++;
		}
		return iCost;
	}

	/**
	 * This method is called from the interface to start evaluation
	 * 
	 */
	@Override
	public void evaluateSolutions() {
		ServiceLocator serviceLocator = ServiceLocator.getInstance();
		solutionTable = serviceLocator.getSolutionTableService();
		callSoftConstrainEvalutors(solutionTable);
	}

	/**
	 * Call the individual soft constrain evaluators for each solution
	 * 
	 * @param solutionTable
	 *            The solutionTable from which solution are taken
	 * 
	 */
	private void callSoftConstrainEvalutors(ISolutionTableService solutionTable) {
		Set<ICurriculum> currentCurriculumSet;
		ICurriculum currentCurricula;
		int numberOfCurriculum, iFairness;
		int[] curriculumCosts = null;

		List<ISolution> notVotedSolutions = solutionTable
				.getNotVotedSolutions();
		for (int i = 0; i < notVotedSolutions.size(); i++) {
			ISolution solutionCode = notVotedSolutions.get(i);
			currentInstance = solutionCode.getProblemInstance();
			currentCode = solutionCode.getCoding();
			currentCurriculumSet = currentInstance.getCurricula();
			numberOfCurriculum = currentInstance.getNumberOfCurricula();
			curriculumCosts = new int[numberOfCurriculum];
			Iterator<ICurriculum> it = currentCurriculumSet.iterator();
			int c = 0;
			int iPenalty = 0;

			// Iterate through each curriculum
			while (it.hasNext()) {
				currentCurricula = it.next();
				// Penalty calculation for given solution
				iPenalty += costsOnRoomCapacity(solutionCode, currentCurricula);
				iPenalty += costsOnMinWorkingDays(solutionCode,
						currentCurricula);
				iPenalty += costsOnCurriculumCompactnessAndRoomStability(
						solutionCode, currentCurricula);
				curriculumCosts[c] = iPenalty;
				c++;
			}

			iFairness = evaluateFairness(curriculumCosts, numberOfCurriculum);

			solutionTable.voteForSolution(i, iPenalty, iFairness);
		}
	}

	/**
	 * Calculates the fairness based on the max, min and avg value of Penalty
	 * for the curriculum. The lower the difference, the better the solution.
	 * 
	 * @param curriculumCosts
	 *            The integer array with the penalty for each curriculum
	 * @param numberOfCurriculum
	 *            Used to find the avg of the Penalties
	 */
	private int evaluateFairness(int[] curriculumCosts, int numberOfCurriculum) {
		int iFairnessCost = 0, maxAvgDiff, minAvgDiff;
		int maxPenalty = -1, minPenalty = -1, avgPenalty = -1, penaltySum = 0;

		// initial value to compare with
		maxPenalty = curriculumCosts[0];
		minPenalty = curriculumCosts[0];
		for (int i = 1; i < numberOfCurriculum; i++) {
			// Take max value, min value, and average value... and compare
			if (curriculumCosts[i] > maxPenalty) {
				maxPenalty = curriculumCosts[i];
			}
			if (curriculumCosts[i] < minPenalty) {
				minPenalty = curriculumCosts[i];
			}
			penaltySum += curriculumCosts[i];
		}
		avgPenalty = (penaltySum / numberOfCurriculum);

		maxAvgDiff = maxPenalty - avgPenalty;
		minAvgDiff = avgPenalty - minPenalty;

		if (maxAvgDiff >= minAvgDiff) {
			// iFairnessCost = maxAvgDiff;
			iFairnessCost = maxAvgDiff - minAvgDiff;
		} else {
			// iFairnessCost = minAvgDiff;
			iFairnessCost = minAvgDiff - maxAvgDiff;
		}

		return iFairnessCost;
	}
}
