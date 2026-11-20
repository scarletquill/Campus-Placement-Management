package com.placement;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class CampusPlacementApp {

    // =========================================================================
    // 1. CUSTOM EXCEPTIONS
    // =========================================================================

    public static class PlacementException extends Exception {
        public PlacementException(String message) { super(message); }
        public PlacementException(String message, Throwable cause) { super(message, cause); }
    }

    public static class EntityNotFoundException extends PlacementException {
        public EntityNotFoundException(String message) { super(message); }
    }

    public static class DuplicateRecordException extends PlacementException {
        public DuplicateRecordException(String message) { super(message); }
    }

    public static class EligibilityException extends PlacementException {
        public EligibilityException(String message) { super(message); }
    }

    public static class StorageException extends PlacementException {
        public StorageException(String message, Throwable cause) { super(message, cause); }
    }

    // =========================================================================
    // 2. DOMAIN MODELS & SERIALIZABLE DATA STORE
    // =========================================================================

    public enum Department { CS, IT, ENTC, MECH, CE }
    public enum PlacementStatus { UNPLACED, PLACED }
    public enum ApplicationStatus { APPLIED, INTERVIEWING, OFFERED, ACCEPTED, REJECTED }

    public static class Student implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String name;
        public String email;
        public Department department;
        public double cgpa;
        public List<String> skills;
        public int graduationYear;
        public PlacementStatus status;

        public Student(int id, String name, String email, Department department,
                       double cgpa, List<String> skills, int graduationYear) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.department = department;
            this.cgpa = cgpa;
            this.skills = new ArrayList<>(skills);
            this.graduationYear = graduationYear;
            this.status = PlacementStatus.UNPLACED;
        }
    }

    public static class Company implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public String name;
        public String industry;
        public String contactEmail;

        public Company(int id, String name, String industry, String contactEmail) {
            this.id = id;
            this.name = name;
            this.industry = industry;
            this.contactEmail = contactEmail;
        }
    }

    public static class JobPosting implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public int companyId;
        public String role;
        public double packageLpa;
        public double minCgpa;
        public List<String> requiredSkills;
        public String location;
        public LocalDate deadline;

        public JobPosting(int id, int companyId, String role, double packageLpa,
                          double minCgpa, List<String> requiredSkills,
                          String location, LocalDate deadline) {
            this.id = id;
            this.companyId = companyId;
            this.role = role;
            this.packageLpa = packageLpa;
            this.minCgpa = minCgpa;
            this.requiredSkills = new ArrayList<>(requiredSkills);
            this.location = location;
            this.deadline = deadline;
        }
    }

    public static class InterviewRound implements Serializable {
        private static final long serialVersionUID = 1L;
        public String roundName;
        public LocalDate date;
        public String result; // Pending, Cleared, Failed

        public InterviewRound(String roundName, LocalDate date, String result) {
            this.roundName = roundName;
            this.date = date;
            this.result = result;
        }
    }

    public static class Offer implements Serializable {
        private static final long serialVersionUID = 1L;
        public double offeredPackageLpa;
        public LocalDate offerDate;
        public String status; // Offered, Accepted, Declined

        public Offer(double offeredPackageLpa, LocalDate offerDate, String status) {
            this.offeredPackageLpa = offeredPackageLpa;
            this.offerDate = offerDate;
            this.status = status;
        }
    }

    public static class Application implements Serializable {
        private static final long serialVersionUID = 1L;
        public int id;
        public int studentId;
        public int jobId;
        public LocalDate appliedDate;
        public ApplicationStatus status;
        public List<InterviewRound> interviews = new ArrayList<>();
        public Offer offer;

        public Application(int id, int studentId, int jobId, LocalDate appliedDate) {
            this.id = id;
            this.studentId = studentId;
            this.jobId = jobId;
            this.appliedDate = appliedDate;
            this.status = ApplicationStatus.APPLIED;
        }
    }

    public static class PlacementStore implements Serializable {
        private static final long serialVersionUID = 1L;
        public Map<Integer, Student> students = new ConcurrentHashMap<>();
        public Map<Integer, Company> companies = new ConcurrentHashMap<>();
        public Map<Integer, JobPosting> jobs = new ConcurrentHashMap<>();
        public Map<Integer, Application> applications = new ConcurrentHashMap<>();

        public AtomicInteger studentSeq = new AtomicInteger(1);
        public AtomicInteger companySeq = new AtomicInteger(1);
        public AtomicInteger jobSeq = new AtomicInteger(1);
        public AtomicInteger appSeq = new AtomicInteger(1);
    }

    // =========================================================================
    // 3. FILE STORAGE & SERIALIZATION MANAGER
    // =========================================================================

    public static class StorageManager {
        public static final Path DATA_DIR = Paths.get("data");
        public static final Path DATA_FILE = DATA_DIR.resolve("placement_data.ser");

        public static void save(PlacementStore store) throws StorageException {
            synchronized (store) {
                try {
                    Files.createDirectories(DATA_DIR);
                    Path tempFile = DATA_DIR.resolve("placement_data.ser.tmp");
                    try (ObjectOutputStream oos = new ObjectOutputStream(
                            new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                        oos.writeObject(store);
                    }
                    try {
                        Files.move(tempFile, DATA_FILE,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tempFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new StorageException("Failed to save data to " + DATA_FILE, e);
                }
            }
        }

        public static PlacementStore load() throws StorageException {
            if (!Files.exists(DATA_FILE)) {
                return new PlacementStore();
            }
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(DATA_FILE)))) {
                Object obj = ois.readObject();
                if (obj instanceof PlacementStore) {
                    return (PlacementStore) obj;
                }
                throw new StorageException("Corrupted data file format.", null);
            } catch (IOException | ClassNotFoundException e) {
                throw new StorageException("Failed to load data from " + DATA_FILE, e);
            }
        }

        public static Path exportSummaryReport(PlacementStore store) throws StorageException {
            synchronized (store) {
                try {
                    Files.createDirectories(DATA_DIR);
                    Path reportPath = DATA_DIR.resolve("placement_report.txt");
                    long total = store.students.size();
                    long placed = store.students.values().stream()
                            .filter(s -> s.status == PlacementStatus.PLACED).count();
                    double rate = total == 0 ? 0.0 : (placed * 100.0) / total;
                    DoubleSummaryStatistics lpaStats = store.applications.values().stream()
                            .filter(a -> a.offer != null && "Accepted".equalsIgnoreCase(a.offer.status))
                            .mapToDouble(a -> a.offer.offeredPackageLpa)
                            .summaryStatistics();

                    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(reportPath))) {
                        out.println("=== CAMPUS PLACEMENT SUMMARY REPORT ===");
                        out.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        out.printf("Total Students  : %d%n", total);
                        out.printf("Placed Students : %d (%.2f%%)%n", placed, rate);
                        out.printf("Total Companies : %d%n", store.companies.size());
                        out.printf("Total Jobs      : %d%n", store.jobs.size());
                        out.printf("Total Apps      : %d%n", store.applications.size());
                        out.printf("Average LPA     : %.2f%n", lpaStats.getCount() > 0 ? lpaStats.getAverage() : 0.0);
                        out.printf("Highest LPA     : %.2f%n", lpaStats.getCount() > 0 ? lpaStats.getMax() : 0.0);
                        out.println("\n--- DEPARTMENT BREAKDOWN ---");
                        Map<Department, List<Student>> byDept = store.students.values().stream()
                                .collect(Collectors.groupingBy(s -> s.department));
                        for (Department dept : Department.values()) {
                            List<Student> list = byDept.getOrDefault(dept, Collections.emptyList());
                            long deptPlaced = list.stream().filter(s -> s.status == PlacementStatus.PLACED).count();
                            double avgCgpa = list.stream().mapToDouble(s -> s.cgpa).average().orElse(0.0);
                            out.printf("%-6s | Total: %-3d | Placed: %-3d | Avg CGPA: %.2f%n",
                                    dept, list.size(), deptPlaced, avgCgpa);
                        }
                    }
                    return reportPath;
                } catch (IOException e) {
                    throw new StorageException("Failed to export text report.", e);
                }
            }
        }

        public static Path exportStudentsCsv(PlacementStore store) throws StorageException {
            synchronized (store) {
                try {
                    Files.createDirectories(DATA_DIR);
                    Path csvPath = DATA_DIR.resolve("students_export.csv");
                    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csvPath))) {
                        out.println("ID,Name,Email,Department,CGPA,GradYear,Status,Skills");
                        store.students.values().stream()
                                .sorted(Comparator.comparingInt(s -> s.id))
                                .forEach(s -> out.printf("%d,\"%s\",\"%s\",%s,%.2f,%d,%s,\"%s\"%n",
                                        s.id, s.name.replace("\"", "'"), s.email,
                                        s.department, s.cgpa, s.graduationYear,
                                        s.status, String.join("; ", s.skills)));
                    }
                    return csvPath;
                } catch (IOException e) {
                    throw new StorageException("Failed to export CSV.", e);
                }
            }
        }
    }

    // =========================================================================
    // 4. BUSINESS LOGIC & STREAMS ANALYTICS SERVICE
    // =========================================================================

    public static class PlacementService {
        private final PlacementStore store;
        private final AtomicBoolean dirtyFlag;

        public PlacementService(PlacementStore store, AtomicBoolean dirtyFlag) {
            this.store = store;
            this.dirtyFlag = dirtyFlag;
        }

        private void markDirty() {
            dirtyFlag.set(true);
        }

        // --- Students ---
        public Student addStudent(String name, String email, Department dept,
                                  double cgpa, List<String> skills, int gradYear)
                throws PlacementException {
            if (cgpa < 0.0 || cgpa > 10.0) {
                throw new PlacementException("CGPA must be between 0.0 and 10.0.");
            }
            synchronized (store) {
                boolean emailExists = store.students.values().stream()
                        .anyMatch(s -> s.email.equalsIgnoreCase(email));
                if (emailExists) {
                    throw new DuplicateRecordException("A student with email '" + email + "' already exists.");
                }
                int id = store.studentSeq.getAndIncrement();
                Student s = new Student(id, name, email, dept, cgpa, skills, gradYear);
                store.students.put(id, s);
                markDirty();
                return s;
            }
        }

        public List<Student> getAllStudents() {
            return store.students.values().stream()
                    .sorted(Comparator.comparingInt(s -> s.id))
                    .collect(Collectors.toList());
        }

        public List<Student> searchStudents(String keyword) {
            String q = keyword.toLowerCase();
            return store.students.values().stream()
                    .filter(s -> s.name.toLowerCase().contains(q)
                            || s.email.toLowerCase().contains(q)
                            || s.department.name().toLowerCase().contains(q)
                            || s.skills.stream().anyMatch(sk -> sk.toLowerCase().contains(q)))
                    .sorted(Comparator.comparingInt(s -> s.id))
                    .collect(Collectors.toList());
        }

        public void deleteStudent(int id) throws PlacementException {
            synchronized (store) {
                if (!store.students.containsKey(id)) {
                    throw new EntityNotFoundException("Student ID " + id + " not found.");
                }
                boolean hasApps = store.applications.values().stream()
                        .anyMatch(a -> a.studentId == id);
                if (hasApps) {
                    throw new PlacementException("Cannot delete student ID " + id + ": linked applications exist.");
                }
                store.students.remove(id);
                markDirty();
            }
        }

        // --- Companies ---
        public Company addCompany(String name, String industry, String contactEmail)
                throws DuplicateRecordException {
            synchronized (store) {
                boolean exists = store.companies.values().stream()
                        .anyMatch(c -> c.name.equalsIgnoreCase(name));
                if (exists) {
                    throw new DuplicateRecordException("Company '" + name + "' already exists.");
                }
                int id = store.companySeq.getAndIncrement();
                Company c = new Company(id, name, industry, contactEmail);
                store.companies.put(id, c);
                markDirty();
                return c;
            }
        }

        public List<Company> getAllCompanies() {
            return store.companies.values().stream()
                    .sorted(Comparator.comparingInt(c -> c.id))
                    .collect(Collectors.toList());
        }

        public void deleteCompany(int id) throws PlacementException {
            synchronized (store) {
                if (!store.companies.containsKey(id)) {
                    throw new EntityNotFoundException("Company ID " + id + " not found.");
                }
                boolean hasJobs = store.jobs.values().stream()
                        .anyMatch(j -> j.companyId == id);
                if (hasJobs) {
                    throw new PlacementException("Cannot delete company ID " + id + ": linked job postings exist.");
                }
                store.companies.remove(id);
                markDirty();
            }
        }

        // --- Job Postings ---
        public JobPosting addJob(int companyId, String role, double packageLpa, double minCgpa,
                                 List<String> requiredSkills, String location, LocalDate deadline)
                throws PlacementException {
            if (packageLpa <= 0 || minCgpa < 0 || minCgpa > 10) {
                throw new PlacementException("Invalid package LPA or minimum CGPA.");
            }
            synchronized (store) {
                if (!store.companies.containsKey(companyId)) {
                    throw new EntityNotFoundException("Company ID " + companyId + " not found.");
                }
                int id = store.jobSeq.getAndIncrement();
                JobPosting job = new JobPosting(id, companyId, role, packageLpa, minCgpa,
                        requiredSkills, location, deadline);
                store.jobs.put(id, job);
                markDirty();
                return job;
            }
        }

        public List<JobPosting> getAllJobs() {
            return store.jobs.values().stream()
                    .sorted(Comparator.comparingInt(j -> j.id))
                    .collect(Collectors.toList());
        }

        public void deleteJob(int id) throws PlacementException {
            synchronized (store) {
                if (!store.jobs.containsKey(id)) {
                    throw new EntityNotFoundException("Job ID " + id + " not found.");
                }
                boolean hasApps = store.applications.values().stream()
                        .anyMatch(a -> a.jobId == id);
                if (hasApps) {
                    throw new PlacementException("Cannot delete job ID " + id + ": linked applications exist.");
                }
                store.jobs.remove(id);
                markDirty();
            }
        }

        public List<Student> findEligibleStudents(int jobId) throws EntityNotFoundException {
            JobPosting job = store.jobs.get(jobId);
            if (job == null) {
                throw new EntityNotFoundException("Job ID " + jobId + " not found.");
            }
            return store.students.values().stream()
                    .filter(s -> s.status == PlacementStatus.UNPLACED)
                    .filter(s -> s.cgpa >= job.minCgpa)
                    .filter(s -> {
                        if (job.requiredSkills.isEmpty()) return true;
                        Set<String> studentSkills = s.skills.stream()
                                .map(String::toLowerCase).collect(Collectors.toSet());
                        return job.requiredSkills.stream()
                                .allMatch(req -> studentSkills.contains(req.toLowerCase()));
                    })
                    .sorted(Comparator.comparingDouble((Student s) -> s.cgpa).reversed())
                    .collect(Collectors.toList());
        }

        // --- Applications, Interviews & Offers ---
        public Application applyForJob(int studentId, int jobId) throws PlacementException {
            synchronized (store) {
                Student student = store.students.get(studentId);
                if (student == null) throw new EntityNotFoundException("Student ID " + studentId + " not found.");
                JobPosting job = store.jobs.get(jobId);
                if (job == null) throw new EntityNotFoundException("Job ID " + jobId + " not found.");

                if (student.status == PlacementStatus.PLACED) {
                    throw new EligibilityException("Student is already placed.");
                }
                if (student.cgpa < job.minCgpa) {
                    throw new EligibilityException(String.format(
                            "CGPA %.2f is below job cutoff %.2f.", student.cgpa, job.minCgpa));
                }
                if (job.deadline.isBefore(LocalDate.now())) {
                    throw new EligibilityException("Application deadline (" + job.deadline + ") has passed.");
                }
                boolean duplicate = store.applications.values().stream()
                        .anyMatch(a -> a.studentId == studentId && a.jobId == jobId);
                if (duplicate) {
                    throw new DuplicateRecordException("Student has already applied to this job.");
                }

                int id = store.appSeq.getAndIncrement();
                Application app = new Application(id, studentId, jobId, LocalDate.now());
                store.applications.put(id, app);
                markDirty();
                return app;
            }
        }

        public List<Application> getAllApplications() {
            return store.applications.values().stream()
                    .sorted(Comparator.comparingInt(a -> a.id))
                    .collect(Collectors.toList());
        }

        public void addInterviewRound(int appId, String roundName, LocalDate date, String result)
                throws EntityNotFoundException {
            synchronized (store) {
                Application app = store.applications.get(appId);
                if (app == null) throw new EntityNotFoundException("Application ID " + appId + " not found.");
                app.interviews.add(new InterviewRound(roundName, date, result));
                if ("Failed".equalsIgnoreCase(result)) {
                    app.status = ApplicationStatus.REJECTED;
                } else if (app.status == ApplicationStatus.APPLIED) {
                    app.status = ApplicationStatus.INTERVIEWING;
                }
                markDirty();
            }
        }

        public void issueOffer(int appId, double offeredLpa) throws PlacementException {
            if (offeredLpa <= 0) throw new PlacementException("Offered package must be positive.");
            synchronized (store) {
                Application app = store.applications.get(appId);
                if (app == null) throw new EntityNotFoundException("Application ID " + appId + " not found.");
                if (app.status == ApplicationStatus.REJECTED) {
                    throw new EligibilityException("Cannot issue offer for a rejected application.");
                }
                app.offer = new Offer(offeredLpa, LocalDate.now(), "Offered");
                app.status = ApplicationStatus.OFFERED;
                markDirty();
            }
        }

        public void updateOfferStatus(int appId, boolean accept) throws PlacementException {
            synchronized (store) {
                Application app = store.applications.get(appId);
                if (app == null) throw new EntityNotFoundException("Application ID " + appId + " not found.");
                if (app.offer == null) throw new PlacementException("No offer has been issued for this application.");

                Student student = store.students.get(app.studentId);
                if (accept) {
                    app.offer.status = "Accepted";
                    app.status = ApplicationStatus.ACCEPTED;
                    if (student != null) student.status = PlacementStatus.PLACED;
                } else {
                    app.offer.status = "Declined";
                    app.status = ApplicationStatus.REJECTED;
                }
                markDirty();
            }
        }

        // --- Streams-Based Analytics ---
        public void printOverallSummary() {
            long totalStudents = store.students.size();
            long placedStudents = store.students.values().stream()
                    .filter(s -> s.status == PlacementStatus.PLACED).count();
            double placementRate = totalStudents == 0 ? 0.0 : (placedStudents * 100.0) / totalStudents;
            double avgCgpa = store.students.values().stream()
                    .mapToDouble(s -> s.cgpa).average().orElse(0.0);

            DoubleSummaryStatistics pkgStats = store.applications.values().stream()
                    .filter(a -> a.offer != null && "Accepted".equalsIgnoreCase(a.offer.status))
                    .mapToDouble(a -> a.offer.offeredPackageLpa)
                    .summaryStatistics();

            System.out.println("\n--- OVERALL PLACEMENT ANALYTICS ---");
            System.out.printf("Total Students      : %d%n", totalStudents);
            System.out.printf("Placed Students     : %d (%.2f%%)%n", placedStudents, placementRate);
            System.out.printf("Average CGPA        : %.2f%n", avgCgpa);
            System.out.printf("Companies / Jobs    : %d / %d%n", store.companies.size(), store.jobs.size());
            System.out.printf("Total Applications  : %d%n", store.applications.size());
            System.out.printf("Accepted Offers     : %d%n", pkgStats.getCount());
            System.out.printf("Average Package LPA : %.2f%n", pkgStats.getCount() > 0 ? pkgStats.getAverage() : 0.0);
            System.out.printf("Highest Package LPA : %.2f%n", pkgStats.getCount() > 0 ? pkgStats.getMax() : 0.0);
        }

        public void printDepartmentBreakdown() {
            System.out.println("\n--- DEPARTMENT-WISE ANALYTICS ---");
            if (store.students.isEmpty()) {
                System.out.println("No student records available.");
                return;
            }
            Map<Department, List<Student>> grouped = store.students.values().stream()
                    .collect(Collectors.groupingBy(s -> s.department));

            System.out.printf("%-8s | %-7s | %-7s | %-10s | %-8s%n",
                    "Dept", "Total", "Placed", "Rate (%)", "Avg CGPA");
            System.out.println("------------------------------------------------------");
            Arrays.stream(Department.values()).forEach(dept -> {
                List<Student> list = grouped.getOrDefault(dept, Collections.emptyList());
                long placed = list.stream().filter(s -> s.status == PlacementStatus.PLACED).count();
                double rate = list.isEmpty() ? 0.0 : (placed * 100.0) / list.size();
                double avgCgpa = list.stream().mapToDouble(s -> s.cgpa).average().orElse(0.0);
                System.out.printf("%-8s | %-7d | %-7d | %9.2f%% | %8.2f%n",
                        dept, list.size(), placed, rate, avgCgpa);
            });
        }

        public void printTopStudents(int limit) {
            System.out.println("\n--- TOP " + limit + " STUDENTS BY CGPA ---");
            List<Student> top = store.students.values().stream()
                    .sorted(Comparator.comparingDouble((Student s) -> s.cgpa).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
            if (top.isEmpty()) {
                System.out.println("No student records available.");
                return;
            }
            System.out.printf("%-4s | %-22s | %-6s | %-6s | %-10s%n",
                    "ID", "Name", "Dept", "CGPA", "Status");
            System.out.println("------------------------------------------------------------");
            top.forEach(s -> System.out.printf("%-4d | %-22s | %-6s | %6.2f | %-10s%n",
                    s.id, truncate(s.name, 22), s.department, s.cgpa, s.status));
        }

        public void printCompanyLeaderboard() {
            System.out.println("\n--- COMPANY HIRING LEADERBOARD ---");
            Map<Integer, List<Application>> offersByCompany = store.applications.values().stream()
                    .filter(a -> a.offer != null && "Accepted".equalsIgnoreCase(a.offer.status))
                    .filter(a -> store.jobs.containsKey(a.jobId))
                    .collect(Collectors.groupingBy(a -> store.jobs.get(a.jobId).companyId));

            if (offersByCompany.isEmpty()) {
                System.out.println("No accepted offers yet.");
                return;
            }

            System.out.printf("%-22s | %-6s | %-10s | %-10s%n",
                    "Company", "Hires", "Avg LPA", "Max LPA");
            System.out.println("-----------------------------------------------------------");
            offersByCompany.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                    .forEach(entry -> {
                        Company c = store.companies.get(entry.getKey());
                        String name = c != null ? c.name : ("Company #" + entry.getKey());
                        DoubleSummaryStatistics stats = entry.getValue().stream()
                                .mapToDouble(a -> a.offer.offeredPackageLpa)
                                .summaryStatistics();
                        System.out.printf("%-22s | %-6d | %10.2f | %10.2f%n",
                                truncate(name, 22), stats.getCount(), stats.getAverage(), stats.getMax());
                    });
        }

        public String getCompanyName(int companyId) {
            Company c = store.companies.get(companyId);
            return c != null ? c.name : ("#" + companyId);
        }

        public String getStudentName(int studentId) {
            Student s = store.students.get(studentId);
            return s != null ? s.name : ("#" + studentId);
        }

        public String getJobLabel(int jobId) {
            JobPosting j = store.jobs.get(jobId);
            return j != null ? (j.role + " @ " + getCompanyName(j.companyId)) : ("#" + jobId);
        }
    }

    // =========================================================================
    // 5. MULTI-THREADED BACKGROUND WORKERS (AUTO-SAVE & NOTIFICATIONS)
    // =========================================================================

    public static class BackgroundWorkers {
        private final PlacementStore store;
        private final AtomicBoolean dirtyFlag;
        private final ConcurrentLinkedQueue<String> unreadNotifications = new ConcurrentLinkedQueue<>();
        private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
        private final ScheduledExecutorService scheduler;
        private volatile String lastAutoSave = "Not yet triggered";

        public BackgroundWorkers(PlacementStore store, AtomicBoolean dirtyFlag) {
            this.store = store;
            this.dirtyFlag = dirtyFlag;
            this.scheduler = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("Placement-Worker-" + t.getId());
                return t;
            });
        }

        public void start() {
            // Thread 1: Auto-save every 30 seconds if data changed
            scheduler.scheduleAtFixedRate(() -> {
                if (dirtyFlag.compareAndSet(true, false)) {
                    try {
                        StorageManager.save(store);
                        lastAutoSave = LocalDateTime.now()
                                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    } catch (StorageException e) {
                        dirtyFlag.set(true); // retry next cycle
                        unreadNotifications.add("[AUTO-SAVE ERROR] " + e.getMessage());
                    }
                }
            }, 30, 30, TimeUnit.SECONDS);

            // Thread 2: Scan for upcoming deadlines, pending interviews & open offers every 20 seconds
            scheduler.scheduleAtFixedRate(this::scanAlerts, 2, 20, TimeUnit.SECONDS);
        }

        private void scanAlerts() {
            LocalDate today = LocalDate.now();
            synchronized (store) {
                for (JobPosting job : store.jobs.values()) {
                    long daysLeft = ChronoUnit.DAYS.between(today, job.deadline);
                    if (daysLeft >= 0 && daysLeft <= 5) {
                        String key = "DEADLINE-" + job.id + "-" + job.deadline;
                        if (seenKeys.add(key)) {
                            Company c = store.companies.get(job.companyId);
                            String compName = c != null ? c.name : ("Company #" + job.companyId);
                            unreadNotifications.add(String.format(
                                    "[DEADLINE] Job #%d (%s @ %s) closes in %d day(s) on %s.",
                                    job.id, job.role, compName, daysLeft, job.deadline));
                        }
                    }
                }
                for (Application app : store.applications.values()) {
                    for (int i = 0; i < app.interviews.size(); i++) {
                        InterviewRound round = app.interviews.get(i);
                        if ("Pending".equalsIgnoreCase(round.result)) {
                            String key = "INTERVIEW-" + app.id + "-" + i;
                            if (seenKeys.add(key)) {
                                unreadNotifications.add(String.format(
                                        "[INTERVIEW] Pending round '%s' for Application #%d (Date: %s).",
                                        round.roundName, app.id, round.date));
                            }
                        }
                    }
                    if (app.offer != null && "Offered".equalsIgnoreCase(app.offer.status)) {
                        String key = "OFFER-" + app.id;
                        if (seenKeys.add(key)) {
                            unreadNotifications.add(String.format(
                                    "[OFFER] Application #%d has an open offer (%.2f LPA) awaiting decision.",
                                    app.id, app.offer.offeredPackageLpa));
                        }
                    }
                }
            }
        }

        public int getUnreadCount() {
            return unreadNotifications.size();
        }

        public List<String> drainNotifications() {
            List<String> list = new ArrayList<>();
            String msg;
            while ((msg = unreadNotifications.poll()) != null) {
                list.add(msg);
            }
            return list;
        }

        public String getLastAutoSave() {
            return lastAutoSave;
        }

        public void stop() {
            scheduler.shutdownNow();
        }
    }

    // =========================================================================
    // 6. CONSOLE UI & ENTRY POINT
    // =========================================================================

    private static final Scanner SCANNER = new Scanner(System.in);

    public static void main(String[] args) {
        PlacementStore store;
        try {
            store = StorageManager.load();
            System.out.println("Loaded store: " + store.students.size() + " students, "
                    + store.companies.size() + " companies, " + store.jobs.size() + " jobs.");
        } catch (StorageException e) {
            System.out.println("Warning: " + e.getMessage() + " Starting with empty store.");
            store = new PlacementStore();
        }

        AtomicBoolean dirtyFlag = new AtomicBoolean(false);
        PlacementService service = new PlacementService(store, dirtyFlag);
        BackgroundWorkers workers = new BackgroundWorkers(store, dirtyFlag);
        workers.start();

        final PlacementStore finalStore = store;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            workers.stop();
            if (dirtyFlag.get()) {
                try {
                    StorageManager.save(finalStore);
                } catch (StorageException ignored) { }
            }
        }));

        boolean running = true;
        while (running) {
            System.out.println("\n================================================");
            System.out.println("         CAMPUS PLACEMENT MANAGEMENT");
            System.out.println("   Auto-Save: " + workers.getLastAutoSave()
                    + (dirtyFlag.get() ? " (unsaved changes)" : " (synced)"));
            System.out.println("================================================");
            System.out.println("1. Manage Students");
            System.out.println("2. Manage Companies");
            System.out.println("3. Manage Job Postings");
            System.out.println("4. Manage Applications & Offers");
            System.out.println("5. Streams Data Analytics");
            System.out.println("6. Save & Export Reports");
            System.out.println("7. Notifications [" + workers.getUnreadCount() + " unread]");
            System.out.println("0. Save & Exit");

            int choice = readInt("Select option: ");
            try {
                switch (choice) {
                    case 1: studentMenu(service); break;
                    case 2: companyMenu(service); break;
                    case 3: jobMenu(service); break;
                    case 4: applicationMenu(service); break;
                    case 5: analyticsMenu(service); break;
                    case 6: storageMenu(store, dirtyFlag); break;
                    case 7: showNotifications(workers); break;
                    case 0:
                        StorageManager.save(store);
                        dirtyFlag.set(false);
                        workers.stop();
                        System.out.println("Data saved to " + StorageManager.DATA_FILE + ". Goodbye!");
                        running = false;
                        break;
                    default:
                        System.out.println("Invalid option. Try again.");
                }
            } catch (PlacementException e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
    }

    // --- Submenus ---

    private static void studentMenu(PlacementService service) throws PlacementException {
        System.out.println("\n--- STUDENTS ---");
        System.out.println("1. Add Student  2. List All  3. Search  4. Delete  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1:
                String name = readRequired("Full Name: ");
                String email = readRequired("Email: ");
                Department dept = readDepartment();
                double cgpa = readDouble("CGPA (0.0 - 10.0): ");
                List<String> skills = readSkills("Skills (comma-separated, optional): ");
                int year = readInt("Graduation Year (e.g. 2027): ");
                Student s = service.addStudent(name, email, dept, cgpa, skills, year);
                System.out.println("Added Student #" + s.id + ": " + s.name);
                break;
            case 2:
                printStudents(service.getAllStudents());
                break;
            case 3:
                String kw = readRequired("Search keyword (name/email/dept/skill): ");
                printStudents(service.searchStudents(kw));
                break;
            case 4:
                int id = readInt("Student ID to delete: ");
                service.deleteStudent(id);
                System.out.println("Deleted Student #" + id);
                break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void companyMenu(PlacementService service) throws PlacementException {
        System.out.println("\n--- COMPANIES ---");
        System.out.println("1. Add Company  2. List All  3. Delete  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1:
                String name = readRequired("Company Name: ");
                String industry = readRequired("Industry: ");
                String email = readRequired("Contact Email: ");
                Company c = service.addCompany(name, industry, email);
                System.out.println("Added Company #" + c.id + ": " + c.name);
                break;
            case 2:
                List<Company> list = service.getAllCompanies();
                if (list.isEmpty()) { System.out.println("No companies found."); return; }
                System.out.printf("%-4s | %-22s | %-16s | %-26s%n", "ID", "Name", "Industry", "Email");
                System.out.println("----------------------------------------------------------------------------");
                list.forEach(comp -> System.out.printf("%-4d | %-22s | %-16s | %-26s%n",
                        comp.id, truncate(comp.name, 22), truncate(comp.industry, 16), truncate(comp.contactEmail, 26)));
                break;
            case 3:
                int id = readInt("Company ID to delete: ");
                service.deleteCompany(id);
                System.out.println("Deleted Company #" + id);
                break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void jobMenu(PlacementService service) throws PlacementException {
        System.out.println("\n--- JOB POSTINGS ---");
        System.out.println("1. Add Job  2. List All  3. Match Eligible Students  4. Delete  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1:
                int compId = readInt("Company ID: ");
                String role = readRequired("Job Role: ");
                double lpa = readDouble("Package (LPA): ");
                double minCgpa = readDouble("Minimum CGPA: ");
                List<String> reqSkills = readSkills("Required Skills (comma-separated, optional): ");
                String loc = readRequired("Location: ");
                LocalDate deadline = readDate("Deadline (YYYY-MM-DD): ");
                JobPosting j = service.addJob(compId, role, lpa, minCgpa, reqSkills, loc, deadline);
                System.out.println("Added Job #" + j.id + ": " + j.role);
                break;
            case 2:
                List<JobPosting> jobs = service.getAllJobs();
                if (jobs.isEmpty()) { System.out.println("No job postings found."); return; }
                System.out.printf("%-4s | %-16s | %-20s | %-6s | %-6s | %-10s | %-12s%n",
                        "ID", "Company", "Role", "LPA", "MinGPA", "Deadline", "Skills");
                System.out.println("-------------------------------------------------------------------------------------------");
                jobs.forEach(job -> System.out.printf("%-4d | %-16s | %-20s | %6.2f | %6.2f | %-10s | %-12s%n",
                        job.id, truncate(service.getCompanyName(job.companyId), 16),
                        truncate(job.role, 20), job.packageLpa, job.minCgpa, job.deadline,
                        truncate(String.join(",", job.requiredSkills), 12)));
                break;
            case 3:
                int jobId = readInt("Job ID: ");
                List<Student> eligible = service.findEligibleStudents(jobId);
                System.out.println("Eligible unplaced students for Job #" + jobId + " (" + eligible.size() + "):");
                printStudents(eligible);
                break;
            case 4:
                int delId = readInt("Job ID to delete: ");
                service.deleteJob(delId);
                System.out.println("Deleted Job #" + delId);
                break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void applicationMenu(PlacementService service) throws PlacementException {
        System.out.println("\n--- APPLICATIONS & OFFERS ---");
        System.out.println("1. Apply for Job  2. List All  3. Add Interview Round  4. Issue Offer  5. Respond to Offer  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1:
                int sId = readInt("Student ID: ");
                int jId = readInt("Job ID: ");
                Application app = service.applyForJob(sId, jId);
                System.out.println("Created Application #" + app.id);
                break;
            case 2:
                List<Application> apps = service.getAllApplications();
                if (apps.isEmpty()) { System.out.println("No applications found."); return; }
                System.out.printf("%-4s | %-18s | %-26s | %-12s | %-6s | %-14s%n",
                        "ID", "Student", "Job", "Status", "Rounds", "Offer");
                System.out.println("-------------------------------------------------------------------------------------------");
                apps.forEach(a -> {
                    String offerTxt = a.offer == null ? "-" : String.format("%.1fL (%s)", a.offer.offeredPackageLpa, a.offer.status);
                    System.out.printf("%-4d | %-18s | %-26s | %-12s | %-6d | %-14s%n",
                            a.id, truncate(service.getStudentName(a.studentId), 18),
                            truncate(service.getJobLabel(a.jobId), 26),
                            a.status, a.interviews.size(), truncate(offerTxt, 14));
                });
                break;
            case 3:
                int appId = readInt("Application ID: ");
                String roundName = readRequired("Round Name (e.g. Technical Round 1): ");
                LocalDate date = readDate("Interview Date (YYYY-MM-DD): ");
                String result = readRequired("Result (Pending / Cleared / Failed): ");
                service.addInterviewRound(appId, roundName, date, result);
                System.out.println("Interview round recorded.");
                break;
            case 4:
                int offerAppId = readInt("Application ID: ");
                double pkg = readDouble("Offered Package (LPA): ");
                service.issueOffer(offerAppId, pkg);
                System.out.println("Offer issued.");
                break;
            case 5:
                int respAppId = readInt("Application ID: ");
                String ans = readRequired("Accept offer? (yes/no): ");
                boolean accept = ans.equalsIgnoreCase("yes") || ans.equalsIgnoreCase("y");
                service.updateOfferStatus(respAppId, accept);
                System.out.println(accept ? "Offer accepted. Student marked PLACED." : "Offer declined.");
                break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void analyticsMenu(PlacementService service) {
        System.out.println("\n--- STREAMS DATA ANALYTICS ---");
        System.out.println("1. Overall Summary  2. Department Breakdown  3. Top-N Students  4. Company Leaderboard  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1: service.printOverallSummary(); break;
            case 2: service.printDepartmentBreakdown(); break;
            case 3:
                int n = readInt("How many top students? ");
                service.printTopStudents(Math.max(1, n));
                break;
            case 4: service.printCompanyLeaderboard(); break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void storageMenu(PlacementStore store, AtomicBoolean dirtyFlag) throws StorageException {
        System.out.println("\n--- STORAGE & EXPORTS ---");
        System.out.println("1. Save Snapshot Now (.ser)  2. Export Summary (.txt)  3. Export Students (.csv)  0. Back");
        int ch = readInt("Choice: ");
        switch (ch) {
            case 1:
                StorageManager.save(store);
                dirtyFlag.set(false);
                System.out.println("Saved binary snapshot to: " + StorageManager.DATA_FILE);
                break;
            case 2:
                Path txt = StorageManager.exportSummaryReport(store);
                System.out.println("Exported summary report to: " + txt);
                break;
            case 3:
                Path csv = StorageManager.exportStudentsCsv(store);
                System.out.println("Exported students CSV to: " + csv);
                break;
            case 0: break;
            default: System.out.println("Invalid choice.");
        }
    }

    private static void showNotifications(BackgroundWorkers workers) {
        System.out.println("\n--- BACKGROUND NOTIFICATIONS ---");
        List<String> alerts = workers.drainNotifications();
        if (alerts.isEmpty()) {
            System.out.println("No unread notifications.");
        } else {
            alerts.forEach(System.out::println);
        }
    }

    // --- Display & Input Helpers ---

    private static void printStudents(List<Student> list) {
        if (list.isEmpty()) {
            System.out.println("No students found.");
            return;
        }
        System.out.printf("%-4s | %-20s | %-6s | %-6s | %-10s | %-24s%n",
                "ID", "Name", "Dept", "CGPA", "Status", "Skills");
        System.out.println("----------------------------------------------------------------------------------");
        list.forEach(s -> System.out.printf("%-4d | %-20s | %-6s | %6.2f | %-10s | %-24s%n",
                s.id, truncate(s.name, 20), s.department, s.cgpa, s.status,
                truncate(String.join(", ", s.skills), 24)));
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    private static String readRequired(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = SCANNER.nextLine().trim();
            if (!line.isEmpty()) return line;
            System.out.println("Value cannot be empty.");
        }
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(SCANNER.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid integer.");
            }
        }
    }

    private static double readDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Double.parseDouble(SCANNER.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid decimal number.");
            }
        }
    }

    private static LocalDate readDate(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalDate.parse(SCANNER.nextLine().trim());
            } catch (DateTimeParseException e) {
                System.out.println("Use YYYY-MM-DD format (e.g. 2026-12-15).");
            }
        }
    }

    private static Department readDepartment() {
        while (true) {
            System.out.print("Department " + Arrays.toString(Department.values()) + ": ");
            try {
                return Department.valueOf(SCANNER.nextLine().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Choose one of: " + Arrays.toString(Department.values()));
            }
        }
    }

    private static List<String> readSkills(String prompt) {
        System.out.print(prompt);
        String line = SCANNER.nextLine().trim();
        if (line.isEmpty()) return new ArrayList<>();
        return Arrays.stream(line.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}