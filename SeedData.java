package com.placement;

import com.placement.CampusPlacementApp.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SeedData {

    private static List<String> skills(String... items) {
        return Arrays.asList(items);
    }

    public static void main(String[] args) {
        try {
            PlacementStore store = new PlacementStore();
            PlacementService service = new PlacementService(store, new AtomicBoolean(false));
            LocalDate today = LocalDate.now();

            // 1. Companies (8)
            Company c1 = service.addCompany("TechCorp", "IT Services", "hr@techcorp.com");
            Company c2 = service.addCompany("CloudNet", "Cloud Computing", "talent@cloudnet.com");
            Company c3 = service.addCompany("FinTech Solutions", "Finance", "hiring@fintech.com");
            Company c4 = service.addCompany("DataSystems", "Analytics", "careers@datasystems.com");
            Company c5 = service.addCompany("WebMinds", "SaaS", "jobs@webminds.com");
            Company c6 = service.addCompany("AutoMakers", "Manufacturing", "hr@automakers.com");
            Company c7 = service.addCompany("BuildIt Infra", "Construction", "careers@buildit.com");
            Company c8 = service.addCompany("ChipWorks", "Semiconductors", "hiring@chipworks.com");

            // 2. Job Postings (10 — three close within 5 days to trigger deadline notifications)
            JobPosting j1  = service.addJob(c1.id, "Software Engineer", 12.5, 8.0, skills("Java", "SQL"), "Pune", today.plusDays(2));
            JobPosting j2  = service.addJob(c1.id, "Frontend Developer", 9.5, 7.5, skills("JavaScript", "React"), "Bangalore", today.plusDays(12));
            JobPosting j3  = service.addJob(c2.id, "Cloud Engineer", 14.0, 8.0, skills("AWS", "Linux"), "Bangalore", today.plusDays(4));
            JobPosting j4  = service.addJob(c3.id, "Quantitative Analyst", 18.0, 8.8, skills("Python", "SQL"), "Mumbai", today.plusDays(15));
            JobPosting j5  = service.addJob(c4.id, "Data Analyst", 8.5, 7.0, skills("Python", "SQL"), "Pune", today.plusDays(10));
            JobPosting j6  = service.addJob(c5.id, "Full Stack Developer", 11.0, 7.5, skills("Java", "React"), "Hyderabad", today.plusDays(3));
            JobPosting j7  = service.addJob(c6.id, "Mechanical Design Eng", 7.5, 7.0, skills("CAD", "SolidWorks"), "Chennai", today.plusDays(20));
            JobPosting j8  = service.addJob(c7.id, "Structural Engineer", 6.8, 6.5, skills("AutoCAD"), "Delhi", today.plusDays(25));
            JobPosting j9  = service.addJob(c8.id, "Embedded Systems Eng", 10.5, 7.8, skills("C++", "VLSI"), "Bangalore", today.plusDays(18));
            JobPosting j10 = service.addJob(c2.id, "DevOps Engineer", 13.0, 7.5, skills("Docker", "Linux"), "Pune", today.plusDays(14));

            // 3. Students (25 across all 5 departments)
            Student s1  = service.addStudent("Aarav Sharma", "aarav.s@edu.in", Department.CS, 9.2, skills("Java", "SQL", "Spring"), 2027);
            Student s2  = service.addStudent("Priya Patel", "priya.p@edu.in", Department.CS, 9.5, skills("Python", "SQL", "ML"), 2027);
            Student s3  = service.addStudent("Rohan Verma", "rohan.v@edu.in", Department.CS, 8.6, skills("Java", "React", "SQL"), 2027);
            Student s4  = service.addStudent("Sneha Kulkarni", "sneha.k@edu.in", Department.CS, 8.1, skills("Java", "SQL", "Docker"), 2027);
            Student s5  = service.addStudent("Kabir Mehta", "kabir.m@edu.in", Department.CS, 7.6, skills("JavaScript", "React", "CSS"), 2027);
            Student s6  = service.addStudent("Ananya Iyer", "ananya.i@edu.in", Department.CS, 8.9, skills("AWS", "Linux", "Docker"), 2027);
            Student s7  = service.addStudent("Devansh Joshi", "devansh.j@edu.in", Department.CS, 6.9, skills("Python", "HTML"), 2027);
            Student s8  = service.addStudent("Meera Nair", "meera.n@edu.in", Department.IT, 9.1, skills("Java", "React", "SQL"), 2027);
            Student s9  = service.addStudent("Siddharth Rao", "siddharth.r@edu.in", Department.IT, 8.4, skills("AWS", "Linux", "Python"), 2027);
            Student s10 = service.addStudent("Kavya Deshmukh", "kavya.d@edu.in", Department.IT, 7.9, skills("Python", "SQL", "Tableau"), 2027);
            Student s11 = service.addStudent("Aditya Gupta", "aditya.g@edu.in", Department.IT, 7.4, skills("JavaScript", "React"), 2027);
            Student s12 = service.addStudent("Ishita Banerjee", "ishita.b@edu.in", Department.IT, 8.7, skills("Docker", "Linux", "Java"), 2027);
            Student s13 = service.addStudent("Vikram Malhotra", "vikram.m@edu.in", Department.ENTC, 8.5, skills("C++", "VLSI", "Python"), 2027);
            Student s14 = service.addStudent("Tanvi Shah", "tanvi.s@edu.in", Department.ENTC, 8.2, skills("C++", "VLSI", "Embedded"), 2027);
            Student s15 = service.addStudent("Pranav Pawar", "pranav.p@edu.in", Department.ENTC, 7.7, skills("Python", "SQL"), 2027);
            Student s16 = service.addStudent("Diya Menon", "diya.m@edu.in", Department.ENTC, 9.0, skills("C++", "Python", "SQL"), 2027);
            Student s17 = service.addStudent("Arjun Singh", "arjun.s@edu.in", Department.MECH, 8.0, skills("CAD", "SolidWorks", "Python"), 2027);
            Student s18 = service.addStudent("Neha Chavan", "neha.c@edu.in", Department.MECH, 7.5, skills("CAD", "SolidWorks"), 2027);
            Student s19 = service.addStudent("Omkar Patil", "omkar.p@edu.in", Department.MECH, 7.1, skills("CAD", "MATLAB"), 2027);
            Student s20 = service.addStudent("Riya Kapoor", "riya.k@edu.in", Department.MECH, 8.3, skills("CAD", "SolidWorks", "SQL"), 2027);
            Student s21 = service.addStudent("Harsh Vardhan", "harsh.v@edu.in", Department.CE, 8.4, skills("AutoCAD", "STAAD"), 2027);
            Student s22 = service.addStudent("Pooja Agarwal", "pooja.a@edu.in", Department.CE, 7.3, skills("AutoCAD", "Revit"), 2027);
            Student s23 = service.addStudent("Nikhil Tiwari", "nikhil.t@edu.in", Department.CE, 6.8, skills("AutoCAD"), 2027);
            Student s24 = service.addStudent("Shruti Jain", "shruti.j@edu.in", Department.CS, 9.4, skills("Python", "SQL", "AWS"), 2027);
            Student s25 = service.addStudent("Yash Kadam", "yash.k@edu.in", Department.IT, 8.2, skills("Java", "SQL", "Linux"), 2027);

            // 4. Applications, Interviews & Offers (22 total)

            // Placed students (9 accepted offers)
            Application a1 = service.applyForJob(s1.id, j1.id);
            service.addInterviewRound(a1.id, "Technical Round 1", today.minusDays(5), "Cleared");
            service.addInterviewRound(a1.id, "HR Round", today.minusDays(2), "Cleared");
            service.issueOffer(a1.id, 12.5);
            service.updateOfferStatus(a1.id, true);

            Application a2 = service.applyForJob(s2.id, j4.id);
            service.addInterviewRound(a2.id, "Aptitude Test", today.minusDays(6), "Cleared");
            service.addInterviewRound(a2.id, "Quant Interview", today.minusDays(3), "Cleared");
            service.issueOffer(a2.id, 18.0);
            service.updateOfferStatus(a2.id, true);

            Application a3 = service.applyForJob(s6.id, j3.id);
            service.addInterviewRound(a3.id, "Cloud Lab Round", today.minusDays(4), "Cleared");
            service.issueOffer(a3.id, 14.5);
            service.updateOfferStatus(a3.id, true);

            Application a4 = service.applyForJob(s8.id, j6.id);
            service.addInterviewRound(a4.id, "Coding Round", today.minusDays(4), "Cleared");
            service.issueOffer(a4.id, 11.5);
            service.updateOfferStatus(a4.id, true);

            Application a5 = service.applyForJob(s12.id, j10.id);
            service.addInterviewRound(a5.id, "System Design", today.minusDays(3), "Cleared");
            service.issueOffer(a5.id, 13.0);
            service.updateOfferStatus(a5.id, true);

            Application a6 = service.applyForJob(s13.id, j9.id);
            service.addInterviewRound(a6.id, "Hardware Technical", today.minusDays(5), "Cleared");
            service.issueOffer(a6.id, 10.5);
            service.updateOfferStatus(a6.id, true);

            Application a7 = service.applyForJob(s17.id, j7.id);
            service.addInterviewRound(a7.id, "CAD Assessment", today.minusDays(4), "Cleared");
            service.issueOffer(a7.id, 7.5);
            service.updateOfferStatus(a7.id, true);

            Application a8 = service.applyForJob(s21.id, j8.id);
            service.addInterviewRound(a8.id, "Structural Round", today.minusDays(3), "Cleared");
            service.issueOffer(a8.id, 6.8);
            service.updateOfferStatus(a8.id, true);

            Application a9 = service.applyForJob(s24.id, j4.id);
            service.addInterviewRound(a9.id, "Quant Interview", today.minusDays(2), "Cleared");
            service.issueOffer(a9.id, 18.5);
            service.updateOfferStatus(a9.id, true);

            // Open offers awaiting decision (2 — triggers offer notifications)
            Application a10 = service.applyForJob(s3.id, j1.id);
            service.addInterviewRound(a10.id, "Technical Round 1", today.minusDays(2), "Cleared");
            service.issueOffer(a10.id, 12.0);

            Application a11 = service.applyForJob(s16.id, j9.id);
            service.addInterviewRound(a11.id, "Technical Round 1", today.minusDays(1), "Cleared");
            service.issueOffer(a11.id, 11.0);

            // Declined offer (1)
            Application a12 = service.applyForJob(s9.id, j5.id);
            service.addInterviewRound(a12.id, "SQL Screening", today.minusDays(4), "Cleared");
            service.issueOffer(a12.id, 8.5);
            service.updateOfferStatus(a12.id, false);

            // Pending interviews (3 — triggers interview notifications)
            Application a13 = service.applyForJob(s4.id, j1.id);
            service.addInterviewRound(a13.id, "Technical Round 1", today.plusDays(2), "Pending");

            Application a14 = service.applyForJob(s14.id, j9.id);
            service.addInterviewRound(a14.id, "VLSI Screening", today.plusDays(3), "Pending");

            Application a15 = service.applyForJob(s25.id, j3.id);
            service.addInterviewRound(a15.id, "Cloud Architecture", today.plusDays(1), "Pending");

            // Rejected during interviews (2)
            Application a16 = service.applyForJob(s5.id, j2.id);
            service.addInterviewRound(a16.id, "UI Coding Test", today.minusDays(2), "Failed");

            Application a17 = service.applyForJob(s18.id, j7.id);
            service.addInterviewRound(a17.id, "Technical Round", today.minusDays(1), "Failed");

            // Fresh applications (5)
            service.applyForJob(s9.id, j3.id);
            service.applyForJob(s10.id, j5.id);
            service.applyForJob(s15.id, j5.id);
            service.applyForJob(s20.id, j7.id);
            service.applyForJob(s22.id, j8.id);

            // Save serialized store
            StorageManager.save(store);

            System.out.println("Sample data successfully saved to " + StorageManager.DATA_FILE);
            System.out.printf("Seeded: %d students, %d companies, %d jobs, %d applications.%n",
                    store.students.size(), store.companies.size(),
                    store.jobs.size(), store.applications.size());

        } catch (PlacementException e) {
            System.err.println("Seeding failed: " + e.getMessage());
        }
    }
}