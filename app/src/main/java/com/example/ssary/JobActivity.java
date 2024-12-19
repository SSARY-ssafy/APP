package com.example.ssary;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    private DatabaseReference favoritesRef;
    private List<Job> jobList = new ArrayList<>();
    private List<Job> filteredJobs = new ArrayList<>();
    private CheckBox checkBoxOnlyActive;
    private EditText searchEditText;
    private ImageView searchIcon, backButton;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private boolean showAllJobs = false;
    private String userUID; // 현재 로그인한 유저의 UID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_job);

        // Firebase Authentication에서 현재 유저 UID 가져오기
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            userUID = currentUser.getUid();
            favoritesRef = FirebaseDatabase.getInstance().getReference("favorites").child(userUID);
        } else {
            Log.e("JobActivity", "User is not logged in!");
        }

        // View 초기화
        recyclerView = findViewById(R.id.recyclerview);
        checkBoxOnlyActive = findViewById(R.id.checkbox_only_active);
        searchEditText = findViewById(R.id.searchEditText);
        searchIcon = findViewById(R.id.searchIcon);
        backButton = findViewById(R.id.back);

        // 뒤로 가기 버튼 동작
        backButton.setOnClickListener(v -> onBackPressed());

        // RecyclerView 설정
        FlexboxLayoutManager flexboxLayoutManager = new FlexboxLayoutManager(this);
        flexboxLayoutManager.setFlexDirection(FlexDirection.ROW);
        flexboxLayoutManager.setJustifyContent(JustifyContent.CENTER);
        recyclerView.setLayoutManager(flexboxLayoutManager);

        adapter = new JobsAdapter(filteredJobs, this);
        recyclerView.setAdapter(adapter);

        // 아이템 클릭 리스너 설정
        adapter.setOnItemClickListener(this::showJobInfoDialog);

        // 검색 아이콘 클릭 시 검색창 표시/숨김
        searchIcon.setOnClickListener(v -> {
            if (searchEditText.getVisibility() == View.GONE) {
                searchEditText.setVisibility(View.VISIBLE);
            } else {
                searchEditText.setVisibility(View.GONE);
            }
        });

        // 검색창 입력 시 필터링
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchJobs(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // "모든 공고 보기" 체크박스 동작
        checkBoxOnlyActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            showAllJobs = isChecked;
            filterJobs();
        });

        fetchJobs();
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
                filterJobs();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("JobActivity", "Error fetching jobs: " + databaseError.getMessage());
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
                e.printStackTrace();
            }
        }

        adapter.updateJobs(filteredJobs);
    }

    private void searchJobs(String query) {
        List<Job> searchResults = new ArrayList<>();
        for (Job job : filteredJobs) {
            if (job.getCompanyName().toLowerCase().contains(query.toLowerCase())) {
                searchResults.add(job);
            }
        }
        adapter.updateJobs(searchResults);
    }

    private void showJobInfoDialog(Job job) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.company_info);

        TextView titleTextView = dialog.findViewById(R.id.titleTextView);
        TextView descriptionTextView = dialog.findViewById(R.id.descriptionTextView);
        TextView linkButton = dialog.findViewById(R.id.linkButton);
        ImageView favoriteButton = dialog.findViewById(R.id.favoriteButton);

        titleTextView.setText("# " + job.getCompanyName());

        String description = String.format(
                Locale.getDefault(),
                "회사명: %s\n채용 직무: %s\n제출 시작: %s\n제출 마감: %s",
                job.getCompanyName(),
                job.getJobPosition(),
                job.getStartDate(),
                job.getEndDate()
        );
        descriptionTextView.setText(description);

        boolean[] isFavorite = {false};

        // 초기화: Firebase에서 즐겨찾기 상태 확인
        if (userUID != null) {
            favoritesRef.child(String.valueOf(job.getJobNumber())).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        favoriteButton.setImageResource(R.drawable.star_full);
                        isFavorite[0] = true;
                    } else {
                        favoriteButton.setImageResource(R.drawable.star_empty);
                        isFavorite[0] = false;
                    }
                }

                @Override
                public void onCancelled(DatabaseError error) {
                    Log.e("JobActivity", "Error checking favorite status: " + error.getMessage());
                }
            });
        }

        favoriteButton.setOnClickListener(v -> {
            if (userUID == null) {
                Log.e("JobActivity", "User not logged in");
                return;
            }

            if (isFavorite[0]) {
                // 즐겨찾기 제거
                favoritesRef.child(String.valueOf(job.getJobNumber())).removeValue((error, ref) -> {
                    if (error == null) {
                        favoriteButton.setImageResource(R.drawable.star_empty);
                        isFavorite[0] = false;
                        Log.d("JobActivity", "Favorite removed for job: " + job.getJobNumber());
                    } else {
                        Log.e("JobActivity", "Error removing favorite: " + error.getMessage());
                    }
                });
            } else {
                // 즐겨찾기 추가
                favoritesRef.child(String.valueOf(job.getJobNumber())).setValue(job, (error, ref) -> {
                    if (error == null) {
                        favoriteButton.setImageResource(R.drawable.star_full);
                        isFavorite[0] = true;
                        Log.d("JobActivity", "Favorite added for job: " + job.getJobNumber());
                    } else {
                        Log.e("JobActivity", "Error adding favorite: " + error.getMessage());
                    }
                });
            }
        });

        linkButton.setOnClickListener(v -> {
            String jobSiteUrl = job.getJobSite();
            if (jobSiteUrl != null && !jobSiteUrl.isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(jobSiteUrl));
                startActivity(intent);
            } else {
                Log.e("JobActivity", "Job site URL is null or empty");
            }
        });

        dialog.show();
    }
}
