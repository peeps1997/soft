package com.peoplesoft.scraper.DAO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.Future;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;

import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.peoplesoft.scraper.Util.Constants;
import com.peoplesoft.scraper.model.PeopleSoftLogin;
import com.peoplesoft.scraper.model.TimesheetApproval;
import com.peoplesoft.scraper.model.WeeklyInsight;

public class PeopleSoftDAO {

	@Async
	public static ListenableFuture<PeopleSoftLogin> tryLogin(final String userName, final String password) {
		final SettableFuture<PeopleSoftLogin> loginFuture = SettableFuture.create();
		try {
			Connection.Response response;
			PeopleSoftLogin dataObject = new PeopleSoftLogin();
			response = Jsoup.connect(Constants.BASE_LOGIN_URL).userAgent(Constants.USERAGENT)
					.timeout(Constants.PEOPLESOFT_TIMEOUT).method(Connection.Method.GET).execute();
			dataObject.loginCookies = response.cookies();
			dataObject.loginCookies.put("PS_DEVICEFEATURES", "");
			dataObject.loginCookies.put("PS_LOGINLIST", "-1");
			dataObject.loginCookies.put("PS_TOKENEXPIRE", "-1");
			response = Jsoup.connect(Constants.BASE_LOGIN_URL).userAgent(Constants.USERAGENT)
					.cookies(dataObject.loginCookies).header("Origin", Constants.BASE_ORIGIN)
					.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
					.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
					.followRedirects(true).header("Content-Type", "application/x-www-form-urlencoded")
					.method(Connection.Method.POST).execute();
			dataObject.loginCookies.remove("PS_LOGINLIST");
			dataObject.loginCookies.remove("PS_TOKENEXPIRE");
			dataObject.loginCookies.remove("PS_TOKEN");
			dataObject.loginCookies.putAll(response.cookies());
			String checkResponse = response.parse().toString().toLowerCase();
			if (checkResponse.contains("peoplesoft sign-in")) {
				dataObject.isPasswordValid = false;
				loginFuture.set(dataObject);
			} else if (checkResponse.contains("employee-facing registry")) {
				dataObject.isPasswordValid = true;
				loginFuture.set(dataObject);
			} else {
				dataObject.isPasswordValid = false;
				loginFuture.set(dataObject);
			}
		} catch (Exception e) {
			e.printStackTrace();
			loginFuture.set(null);
		}

		return loginFuture;
	}

	@Async
	public static ListenableFuture<LinkedList<TimesheetApproval>> searchEmployeesPendingReportsViaProject(
			String projectID, String date, String userName, String password) {
		final SettableFuture<LinkedList<TimesheetApproval>> employeeFuture = SettableFuture.create();
		try {
			Map<String, String> loginCookies = new HashMap<String, String>();
			Connection.Response response;
			LinkedList<TimesheetApproval> employeeData = new LinkedList<TimesheetApproval>();
			loginCookies.put("PS_DEVICEFEATURES", "");
			long startTime = System.nanoTime();
			if (date != null && date.trim().length() > 0) {
				response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			} else {
				response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			}
			System.out.println("1: " + ((double) ((System.nanoTime() - startTime) / 1000000000.0)));
			startTime = System.nanoTime();
			loginCookies.remove("PS_LOGINLIST");
			loginCookies.remove("PS_TOKENEXPIRE");
			loginCookies.remove("PS_TOKEN");
			loginCookies.putAll(response.cookies());
			if (date != null && date.trim().length() > 0) {
				response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT)
						.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
						.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID).data(Constants.SEARCH_DATE_VALUE, date)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			} else {
				response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT)
						.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
						.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			}
			Document responseDocument = Jsoup.parse(response.body());
			Elements rowLeftHalf = responseDocument.select("table[id=tdgblTL_MGR_SRCH_VW$0] > tbody > tr");
			Elements rowsRightHalf = responseDocument.select("table[id=tdgbrTL_MGR_SRCH_VW$0] > tbody > tr");
			int count = 0;
			System.out.println("2: " + ((double) ((System.nanoTime() - startTime) / 1000000000.0)));
			if (rowLeftHalf.size() != 0) {
				ArrayList<Future<TimesheetApproval>> futures = new ArrayList<Future<TimesheetApproval>>();
				for (int i = 0; i < rowLeftHalf.size(); i++) {
					ListenableFuture<TimesheetApproval> timesheetFuture = JdkFutureAdapters.listenInPoolThread(
							PeopleSoftDAO.computeTimesheet(loginCookies, projectID, count, rowLeftHalf.get(i),
									rowsRightHalf.get(i), date, userName, password, Constants.PENDING));
					futures.add(timesheetFuture);

					count++;
				}
				for (Future<TimesheetApproval> future : futures) {
					try {
						if (future.get() != null) {
							employeeData.add(future.get());
						}
					} catch (Exception ignored) {
					}
				}
				if (employeeData != null && employeeData.size() != 0)
					employeeFuture.set(employeeData);
				else
					employeeFuture.set(null);
			} else {
				employeeFuture.set(null);
			}
		} catch (Exception e) {
			e.printStackTrace();
			employeeFuture.set(null);
		}

		return employeeFuture;
	}

	@Async
	public static ListenableFuture<LinkedList<TimesheetApproval>> searchEmployeesApprovedReportsViaProject(
			String projectID, String date, String userName, String password) {
		final SettableFuture<LinkedList<TimesheetApproval>> employeeFuture = SettableFuture.create();
		try {
			Map<String, String> loginCookies = new HashMap<String, String>();
			Connection.Response response;
			LinkedList<TimesheetApproval> employeeData = new LinkedList<TimesheetApproval>();
			loginCookies.put("PS_DEVICEFEATURES", "");
			long startTime = System.nanoTime();
			if (date != null && date.trim().length() > 0) {
				response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			} else {
				response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			}
			System.out.println("1: " + ((double) ((System.nanoTime() - startTime) / 1000000000.0)));
			startTime = System.nanoTime();
			loginCookies.remove("PS_LOGINLIST");
			loginCookies.remove("PS_TOKENEXPIRE");
			loginCookies.remove("PS_TOKEN");
			loginCookies.putAll(response.cookies());
			if (date != null && date.trim().length() > 0) {
				response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT)
						.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
						.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID).data(Constants.SEARCH_DATE_VALUE, date)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			} else {
				response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT)
						.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
						.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
			}
			Document responseDocument = Jsoup.parse(response.body());
			Elements rowLeftHalf = responseDocument.select("table[id=tdgblTL_MGR_SRCH_VW$0] > tbody > tr");
			Elements rowsRightHalf = responseDocument.select("table[id=tdgbrTL_MGR_SRCH_VW$0] > tbody > tr");
			String empCount = responseDocument.select("span[class=PSGRIDCOUNTER]").text();
			String[] emps = empCount.split(" ");
			System.out.println("Time Before MultiThreading: "+ System.currentTimeMillis());
			System.out.println("(inside getProj)EMP Count rn: "+ empCount);
			int empC = Integer.parseInt(emps[2]);
			for(int i = 0;i<empC;i++) {
			System.out.println("(inside computeTimesheet)EMP name rn: "+ responseDocument.select("a[id=LAST_NAME$"+i+"]").text());}
			int count = 0;
			System.out.println("2: " + ((double) ((System.nanoTime() - startTime) / 1000000000.0)));
			if (rowLeftHalf.size() != 0) {
				ArrayList<Future<TimesheetApproval>> futures = new ArrayList<Future<TimesheetApproval>>();
				for (int i = 0; i < rowLeftHalf.size(); i++) {
					ListenableFuture<TimesheetApproval> timesheetFuture = JdkFutureAdapters.listenInPoolThread(
							PeopleSoftDAO.computeTimesheet(loginCookies, projectID, count, rowLeftHalf.get(i),
									rowsRightHalf.get(i), date, userName, password, Constants.APPROVED));
					futures.add(timesheetFuture);

					count++;
				}
				for (Future<TimesheetApproval> future : futures) {
					try {
						if (future.get() != null) {
							employeeData.add(future.get());
						}
					} catch (Exception ignored) {
					}
				}
				if (employeeData != null && employeeData.size() != 0)
					employeeFuture.set(employeeData);
				else
					employeeFuture.set(null);
			} else {
				employeeFuture.set(null);
			}
		} catch (Exception e) {
			e.printStackTrace();
			employeeFuture.set(null);

		}

		return employeeFuture;
	}

	@Async
	public static ListenableFuture<TimesheetApproval> computeTimesheet(Map<String, String> loginCookies,
			String projectID, int count, Element rowLeftHalf, Element rowRightHalf, String date, String userName,
			String password, int choice) {
		long startTime = System.nanoTime();
		System.out.println("Thread ID: "+Thread.currentThread().getId());
		try {
			Connection.Response response;
			if (choice == Constants.APPROVED) {
				response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
						.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
						.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
						.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
						.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
						.execute();
				loginCookies.remove("PS_LOGINLIST");
				loginCookies.remove("PS_TOKENEXPIRE");
				loginCookies.remove("PS_TOKEN");
				loginCookies.putAll(response.cookies());
				if (date != null && date.trim().length() > 0) {
					response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
							.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
							.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
							.timeout(Constants.PEOPLESOFT_TIMEOUT)
							.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
							.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID).data(Constants.SEARCH_DATE_VALUE, date)
							.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
							.execute();
				} else {
					response = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
							.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
							.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
							.timeout(Constants.PEOPLESOFT_TIMEOUT)
							.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
							.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID)
							.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
							.execute();
				}

				if(!response.body().isEmpty()) {
				Document responseDocument = Jsoup.parse(response.body());
				String empCount = responseDocument.select("span[class=PSGRIDCOUNTER]").text();
				System.out.println("(inside computeTimesheet)EMP Count rn: "+ empCount);
				System.out.println("(inside computeTimesheet)EMP name rn: "+ responseDocument.select("span[id=FIRST_NAME**]").text());
				}
			} else if (choice == Constants.PENDING) {
					response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
							.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
							.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
							.timeout(Constants.PEOPLESOFT_TIMEOUT).data("userid", userName).data("pwd", password)
							.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
							.execute();
				loginCookies.remove("PS_LOGINLIST");
				loginCookies.remove("PS_TOKENEXPIRE");
				loginCookies.remove("PS_TOKEN");
				loginCookies.putAll(response.cookies());
				if (date != null && date.trim().length() > 0) {
					response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
							.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
							.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
							.timeout(Constants.PEOPLESOFT_TIMEOUT)
							.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
							.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID).data(Constants.SEARCH_DATE_VALUE, date)
							.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
							.execute();
				} else {
					response = Jsoup.connect(Constants.TIMESHEET_PENDING_APPROVALS_URL).userAgent(Constants.USERAGENT)
							.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN)
							.header("Host", Constants.BASE_HOST).referrer(Constants.BASE_REFERER)
							.timeout(Constants.PEOPLESOFT_TIMEOUT)
							.data(Constants.SEARCH_EMPLOYEE, Constants.SEARCH_EMPLOYEE_VALUE)
							.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID)
							.header("Content-Type", "application/x-www-form-urlencoded").method(Connection.Method.POST)
							.execute();
				}

				if(!response.body().isEmpty()) {
				Document responseDocument = Jsoup.parse(response.body());
				String empCount = responseDocument.select("span[class=PSGRIDCOUNTER]").text();
				System.out.println("(inside computeTimesheet)EMP Count rn: "+ empCount);}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		final SettableFuture<TimesheetApproval> timesheetFuture = SettableFuture.create();
		try {
			TimesheetApproval timeObject = new TimesheetApproval();
			timeObject.lastName = rowLeftHalf.selectFirst("a[id=LAST_NAME$" + count + "]").text();
			timeObject.firstName = rowLeftHalf.selectFirst("span[id=FIRST_NAME$" + count + "]").text();
			timeObject.employeeID = rowLeftHalf.selectFirst("span[id=EMPLID$" + count + "]").text();
			timeObject.jobTitle = rowRightHalf.selectFirst("span[id=JOB_DESCR$" + count + "]").text();
			timeObject.approvalHours = rowRightHalf.selectFirst("span[id=TOTAL_PEND_HRS$" + count + "]").text();
			if (choice == Constants.PENDING) {
				timeObject.reportedHours = rowRightHalf.selectFirst("span[id=TOTAL_RPTD_HRS$" + count + "]").text();
			} else {
				timeObject.reportedHours = rowRightHalf.selectFirst("span[id=TOTAL_RPTD_HRS1$" + count + "]").text();
			}
			timeObject.scheduledHours = rowRightHalf.selectFirst("span[id=TOTAL_SCH_HRS$" + count + "]").text();
			try {
				timeObject.absenceHours = rowRightHalf.selectFirst("span[id=TL_ABSENCE_LNK$" + count + "]").text();
			} catch (Exception es) {
				timeObject.absenceHours = "0.00";
			}
			System.out.println("LAST_NAME$" + count);
			timeObject.hoursApproved = rowRightHalf.selectFirst("span[id=TOTAL_APRV_HRS$" + count + "]").text();
			timeObject.hoursDenied = rowRightHalf.selectFirst("span[id=TOTAL_DENY_HRS$" + count + "]").text();
			Document rowResponseDocument = Jsoup.connect(Constants.TIMESHEET_REPORTS_URL).userAgent(Constants.USERAGENT)
					.cookies(loginCookies).header("Origin", Constants.BASE_ORIGIN).header("Host", Constants.BASE_HOST)
					.referrer(Constants.TIMESHEET_REPORTS_URL).timeout(Constants.PEOPLESOFT_TIMEOUT)
					.data("VALUE$1", timeObject.employeeID).data(Constants.SEARCH_EMPLOYEE, "LAST_NAME$" + count)
					.data(Constants.SEARCH_PROJECT_ID_VALUE, projectID)
					.header("Content-Type", "application/x-www-form-urlencoded").post();
			Elements subRows = rowResponseDocument.select("table[id=tdgblTR_WEEKLY_GRID$0] > tbody > tr");
			WeeklyInsight weekReport = new WeeklyInsight();
			System.out.println("Rsize " + subRows.size());
			for (int j = 0; j < subRows.size(); j++) {
				Element subRow = subRows.get(j);
				try {
					if (subRow.selectFirst("span[id=QTY_DAY1$" + j + "]").text().trim().length() > 1) {
						weekReport.sunday = subRow.selectFirst("span[id=QTY_DAY1$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.sundayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.sundayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.sundayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY2$" + j + "]").text().trim().length() > 1) {
						weekReport.monday = subRow.selectFirst("span[id=QTY_DAY2$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.mondayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.mondayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.mondayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY3$" + j + "]").text().trim().length() > 1) {
						weekReport.tuesday = subRow.selectFirst("span[id=QTY_DAY3$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.tuesdayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.tuesdayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.tuesdayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY4$" + j + "]").text().trim().length() > 1) {
						weekReport.wednesday = subRow.selectFirst("span[id=QTY_DAY4$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.wednesdayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.wednesdayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.wednesdayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY5$" + j + "]").text().trim().length() > 1) {
						weekReport.thursday = subRow.selectFirst("span[id=QTY_DAY5$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.thursdayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.thursdayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.thursdayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY6$" + j + "]").text().trim().length() > 1) {
						weekReport.friday = subRow.selectFirst("span[id=QTY_DAY6$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.fridayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.fridayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.fridayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
				try {
					if (subRow.selectFirst("span[id=QTY_DAY7$" + j + "]").text().trim().length() > 1) {
						weekReport.saturday = subRow.selectFirst("span[id=QTY_DAY7$" + j + "]").text();
						if (choice == Constants.APPROVED) {
							try {
								if (subRow.selectFirst("select[id=TRC$" + j + "] option[selected]").text().trim()
										.length() > 1) {
									weekReport.saturdayCode = subRow
											.selectFirst("select[id=TRC$" + j + "] option[selected]").text();
								}
							} catch (Exception e) {
								weekReport.saturdayCode = subRow.selectFirst("span[id=TRC$" + j + "]").text();
							}
						} else {
							weekReport.saturdayCode = subRow.selectFirst("select[id=TRC$" + j + "] option[selected]")
									.text();
						}
					}
				} catch (Exception ignored) {
				}
			}
			try {
				if (weekReport.sunday == null || weekReport.sunday.trim().equals("")) {
					weekReport.sunday = "N/A";
					weekReport.sundayCode = "N/A";
				}
				if (weekReport.monday == null || weekReport.monday.trim().equals("")) {
					weekReport.monday = "N/A";
					weekReport.mondayCode = "N/A";
				}
				if (weekReport.tuesday == null || weekReport.tuesday.trim().equals("")) {
					weekReport.tuesday = "N/A";
					weekReport.tuesdayCode = "N/A";
				}
				if (weekReport.wednesday == null || weekReport.wednesday.trim().equals("")) {
					weekReport.wednesday = "N/A";
					weekReport.wednesdayCode = "N/A";
				}
				if (weekReport.thursday == null || weekReport.thursday.trim().equals("")) {
					weekReport.thursday = "N/A";
					weekReport.thursdayCode = "N/A";
				}
				if (weekReport.friday == null || weekReport.friday.trim().equals("")) {
					weekReport.friday = "N/A";
					weekReport.fridayCode = "N/A";
				}
				if (weekReport.saturday == null || weekReport.saturday.trim().equals("")) {
					weekReport.saturday = "N/A";
					weekReport.saturdayCode = "N/A";
				}
				System.out.println("Sub" + count + ": " + ((double) ((System.nanoTime() - startTime) / 1000000000.0)));
			} catch (Exception ignored) {
			}
			timeObject.report = weekReport;
			timesheetFuture.set(timeObject);
		} catch (Exception e) {
			e.printStackTrace();
			timesheetFuture.set(null);
		}
		return timesheetFuture;
	}
}
