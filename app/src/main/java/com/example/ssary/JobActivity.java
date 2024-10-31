package com.example.ssary;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SearchView;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JobActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private JobsAdapter adapter;
    private DatabaseReference databaseReference;
    private List<Job> jobList = new ArrayList<>();
    private List<Job> filteredJobs = new ArrayList<>();
    private List<Job> displayedJobs = new ArrayList<>();
    private CheckBox checkBoxOnlyActive;
    private SearchView searchView;
    private TextView pageNumberText;
    private ImageView nextPageButton, prevPageButton;
    private int currentPage = 0;
    private final int itemsPerPage = 30;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private boolean showAllJobs = false;
    private ImageView backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job);

        recyclerView = findViewById(R.id.recyclerview);

        // FlexboxLayoutManager 설정
        FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(this);
        flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexboxLayoutManager.setJustifyContent(JustifyContent.CENTER);
        recyclerView.setLayoutManager(flexboxLayoutManager);

        checkBoxOnlyActive = findViewById(R.id.checkbox_only_active);
        searchView = findViewById(R.id.search);
        pageNumberText = findViewById(R.id.page_number);
        nextPageButton = findViewById(R.id.next_page_button);
        prevPageButton = findViewById(R.id.prev_page_button);
        backButton = findViewById(R.id.back);

        backButton.setOnClickListener(v -> onBackPressed());

        adapter = new JobsAdapter(displayedJobs, this);
        recyclerView.setAdapter(adapter);

        // 어댑터 아이템 클릭 시 Dialog 표시 설정
        adapter.setOnItemClickListener(this::showJobInfoDialog);

        fetchJobs();

        checkBoxOnlyActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showAllJobs = isChecked;
            filterJobs();
            currentPage = 0;
            displayPage(currentPage);
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchJobs(newText);
                return true;
            }
        });

        prevPageButton.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                displayPage(currentPage);
            }
        });

        nextPageButton.setOnClickListener(v -> {
            if ((currentPage + 1) * itemsPerPage < filteredJobs.size()) {
                currentPage++;
                displayPage(currentPage);
            }
        });
    }

    // Dialog 생성 및 표시 메서드
    private void showJobInfoDialog(Job job) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.company_info); // company_info.xml 레이아웃 설정

        // company_info.xml의 요소 초기화
        TextView titleTextView = dialog.findViewById(R.id.titleTextView);
        TextView descriptionTextView = dialog.findViewById(R.id.descriptionTextView);
        TextView linkButton = dialog.findViewById(R.id.linkButton);
        ImageView favoriteButton = dialog.findViewById(R.id.favoriteButton);

        // Job 객체의 데이터로 Dialog 설정
        titleTextView.setText("# " + job.getCompanyName());
        descriptionTextView.setText(
                "회사명: " + job.getCompanyName() + "\n"
                        + "채용 직무: " + job.getJobPosition() + "\n"
                        + "제출 시작: " + job.getStartDate() + "\n"
                        + "제출 마감: " + job.getEndDate()
        );

        // 초기 즐겨찾기 상태 설정
        boolean[] isFavorite = {false}; // 배열로 선언하여 내부 클래스에서도 값 변경 가능
        favoriteButton.setImageResource(R.drawable.star_empty); // 초기 상태를 비어있는 별로 설정

        // 즐겨찾기 버튼 클릭 시 아이콘 변경
        favoriteButton.setOnClickListener(v -> {
            if (isFavorite[0]) {
                favoriteButton.setImageResource(R.drawable.star_empty);
            } else {
                favoriteButton.setImageResource(R.drawable.star_full);
            }
            isFavorite[0] = !isFavorite[0]; // 상태 토글
        });

        // 링크 버튼 클릭 시 채용 공고 사이트로 이동
        linkButton.setOnClickListener(v -> {
            String jobSiteUrl = job.getJobSite();
            if (jobSiteUrl != null && !jobSiteUrl.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(jobSiteUrl)));
            }
        });

        dialog.show(); // Dialog 표시
    }


    private void fetchJobs() {
        databaseReference = FirebaseDatabase.getInstance().getReference("JOB");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                jobList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Job job = snapshot.getValue(Job.class);
                    if (job != null) {
                        jobList.add(job);
                    }
                }
                Log.d("JobActivity", "Total jobs fetched: " + jobList.size());
                filterJobs();
                currentPage = 0;
                displayPage(currentPage);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("JobActivity", "Error loading data", databaseError.toException());
            }
        });
    }

    private void filterJobs() {
        filteredJobs.clear();
        Date today = new Date();
        boolean onlyActive = !showAllJobs;

        for (Job job : jobList) {
            try {
                Date endDate = sdf.parse(job.getEndDate());
                if (endDate != null && (onlyActive && endDate.after(today)) || showAllJobs) {
                    filteredJobs.add(job);
                }
            } catch (ParseException e) {
                Log.e("JobActivity", "Error parsing date", e);
            }
        }

        currentPage = 0;
        displayPage(currentPage);
    }

    private void searchJobs(String query) {
        List<Job> searchResults = new ArrayList<>();
        for (Job job : filteredJobs) {
            if (job.getCompanyName().toLowerCase().contains(query.toLowerCase())) {
                searchResults.add(job);
            }
        }
        filteredJobs.clear();
        filteredJobs.addAll(searchResults);
        currentPage = 0;
        displayPage(currentPage);
    }

    private void displayPage(int page) {
        displayedJobs.clear();
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filteredJobs.size());

        for (int i = start; i < end; i++) {
            displayedJobs.add(filteredJobs.get(i));
        }

        Log.d("JobActivity", "Displayed jobs on page " + (currentPage + 1) + ": " + displayedJobs.size());
        adapter.updateJobs(displayedJobs);

        int totalPages = (int) Math.ceil((double) filteredJobs.size() / itemsPerPage);
        pageNumberText.setText(String.format(Locale.getDefault(), "%d / %d", currentPage + 1, totalPages));

        prevPageButton.setEnabled(currentPage > 0);
        nextPageButton.setEnabled(currentPage < totalPages - 1);
    }
}
