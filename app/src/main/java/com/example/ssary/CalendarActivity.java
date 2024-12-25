package com.example.ssary;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.text.LineBreaker;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import android.app.DatePickerDialog;
import android.util.TypedValue;
import android.text.TextUtils;



import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class CalendarActivity extends AppCompatActivity {

    private TextView tvCurrentMonth;
    private ImageButton btnPrevMonth, btnNextMonth, btnAddSchedule, btnBack;
    private GridLayout calendarGrid;

    private Calendar calendar;
    private int currentYear, currentMonth;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        // View 초기화
        tvCurrentMonth = findViewById(R.id.tvCurrentMonth);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        btnAddSchedule = findViewById(R.id.btnAddSchedule);
        btnBack = findViewById(R.id.btnBack);
        calendarGrid = findViewById(R.id.calendarGrid);

        db = FirebaseFirestore.getInstance();

        // 날짜 초기화
        calendar = Calendar.getInstance();
        currentYear = calendar.get(Calendar.YEAR);
        currentMonth = calendar.get(Calendar.MONTH);

        updateCalendar();

        // 버튼 리스너 설정
        btnPrevMonth.setOnClickListener(v -> changeMonth(-1));
        btnNextMonth.setOnClickListener(v -> changeMonth(1));
        btnAddSchedule.setOnClickListener(v -> {
            showAddScheduleDialog(null, this::updateCalendar);
        });
        btnBack.setOnClickListener(v -> finish()); // 뒤로가기
    }

    private void changeMonth(int delta) {
        currentMonth += delta;
        if (currentMonth > 11) {
            currentMonth = 0;
            currentYear++;
        } else if (currentMonth < 0) {
            currentMonth = 11;
            currentYear--;
        }
        updateCalendar();
    }

    private void updateCalendar() {
        tvCurrentMonth.setText(String.format("%d.%02d", currentYear, currentMonth + 1));
        calendarGrid.removeAllViews();

        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth, 1);

        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        cal.add(Calendar.MONTH, -1);
        int prevMonthDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = 35; // 5주 출력
        int dayCounter = 1;

        for (int i = 0; i < totalCells; i++) {
            // 날짜 셀 레이아웃
            LinearLayout dayCell = new LinearLayout(this);
            dayCell.setOrientation(LinearLayout.VERTICAL);
            dayCell.setPadding(4, 4, 4, 4);
            dayCell.setBackgroundResource(R.drawable.grid_border);

            // 날짜 텍스트
            TextView dayText = new TextView(this);
            dayText.setGravity(Gravity.CENTER);
            dayText.setTextSize(14);
            dayText.setTextColor(Color.BLACK);

            if (i < firstDayOfWeek) { // 이전달 날짜
                dayText.setText(String.valueOf(prevMonthDays - (firstDayOfWeek - i - 1)));
                dayText.setTextColor(Color.GRAY);
            } else if (dayCounter <= daysInMonth) { // 현재달 날짜
                dayText.setText(String.valueOf(dayCounter));
                final int selectedDay = dayCounter;
                dayCell.setOnClickListener(v -> showScheduleDialog(selectedDay));

                addScheduleBars(dayCell, dayCounter);
                dayCounter++;
            } else { // 다음달 날짜
                dayText.setText(String.valueOf(dayCounter - daysInMonth));
                dayText.setTextColor(Color.GRAY);
                dayCounter++;
            }

            dayCell.addView(dayText);

            // 날짜 셀 레이아웃 파라미터 설정
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.height = 0;
            params.width = 0;
            params.rowSpec = GridLayout.spec(i / 7, 1f);
            params.columnSpec = GridLayout.spec(i % 7, 1f);
            dayCell.setLayoutParams(params);

            calendarGrid.addView(dayCell);
        }
    }

    private void addScheduleBars(LinearLayout dayCell, int day) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            return;
        }

        String uid = user.getUid();
        String dateKey = createValidDateKey(currentYear, currentMonth + 1, day); // 유효한 키 생성

        DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                .getReference("schedule")
                .child(uid)
                .child(dateKey);

        scheduleRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                int maxVisibleSchedules = 4; // 최대 표시 개수
                int barCount = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    if (barCount >= maxVisibleSchedules) break;

                    String content = snapshot.child("content").getValue(String.class);
                    if (content == null) continue;

                    TextView scheduleBar = new TextView(CalendarActivity.this);
                    scheduleBar.setText(content);
                    scheduleBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    scheduleBar.setPadding(4, 2, 4, 2);
                    scheduleBar.setBackgroundColor(Color.parseColor("#448AFF")); // 파란색 배경
                    scheduleBar.setTextColor(Color.WHITE);
                    scheduleBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    scheduleBar.setMaxLines(1);
                    scheduleBar.setEllipsize(TextUtils.TruncateAt.END);

                    LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            48
                    );
                    barParams.setMargins(0, 4, 0, 0);
                    scheduleBar.setLayoutParams(barParams);

                    dayCell.addView(scheduleBar);
                    barCount++;
                }

                // 추가 일정이 있을 경우 "..." 표시
                if (dataSnapshot.getChildrenCount() > maxVisibleSchedules) {
                    TextView moreText = new TextView(CalendarActivity.this);
                    moreText.setText("...");
                    moreText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    moreText.setGravity(Gravity.CENTER);
                    moreText.setTextColor(Color.DKGRAY);

                    LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    moreParams.setMargins(0, 4, 0, 0);
                    moreText.setLayoutParams(moreParams);

                    dayCell.addView(moreText);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CalendarActivity.this, "데이터를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void showScheduleDialog(int day) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_schedule_list);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView tvSelectedDate = dialog.findViewById(R.id.tvSelectedDate);
        LinearLayout scheduleListContainer = dialog.findViewById(R.id.scheduleListContainer);
        LinearLayout jobListContainer = dialog.findViewById(R.id.jobListContainer);
        View divider = dialog.findViewById(R.id.divider);
        TextView tvJobHeader = dialog.findViewById(R.id.tv_job_header);
        ImageButton btnAddSchedule = dialog.findViewById(R.id.btnAddSchedule);

        divider.setVisibility(View.GONE);
        tvJobHeader.setVisibility(View.GONE);

        // 선택된 날짜
        tvSelectedDate.setText(String.format("%d년 %02d월 %02d일", currentYear, currentMonth + 1, day));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        String dateKey = createValidDateKey(currentYear, currentMonth + 1, day); // 유효한 키 생성

        Runnable updateScheduleList = () -> {
            DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                    .getReference("schedule")
                    .child(uid)
                    .child(dateKey);

            scheduleRef.addListenerForSingleValueEvent(new ValueEventListener() {
                // 클래스 멤버 변수로 선언
                private View.OnClickListener originalEditListener;

                // onDataChange 메서드 내 수정
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    scheduleListContainer.removeAllViews();
                    for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                        String content = snapshot.child("content").getValue(String.class);
                        String scheduleKey = snapshot.getKey(); // 데이터 수정 및 삭제를 위한 키
                        if (content == null || scheduleKey == null) continue;

                        // item_schedule 레이아웃 추가
                        View scheduleItem = getLayoutInflater().inflate(R.layout.item_schedule, scheduleListContainer, false);
                        TextView tvContent = scheduleItem.findViewById(R.id.tvScheduleContent);
                        Button btnDelete = scheduleItem.findViewById(R.id.btnDelete);
                        Button btnEdit = scheduleItem.findViewById(R.id.btnEdit);

                        tvContent.setText(content);

                        // 삭제 버튼 클릭 리스너
                        btnDelete.setOnClickListener(v -> {
                            new AlertDialog.Builder(CalendarActivity.this)
                                    .setTitle("삭제 확인")
                                    .setMessage("정말로 삭제하시겠습니까?")
                                    .setPositiveButton("확인", (dialog, which) -> {
                                        // Firebase Realtime Database에서 데이터 삭제
                                        DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                                                .getReference("schedule")
                                                .child(uid)
                                                .child(dateKey)
                                                .child(scheduleKey);

                                        scheduleRef.removeValue()
                                                .addOnSuccessListener(aVoid -> {
                                                    Toast.makeText(CalendarActivity.this, "일정이 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                                    scheduleListContainer.removeView(scheduleItem); // UI에서 제거
                                                    updateCalendar();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Toast.makeText(CalendarActivity.this, "삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                                });
                                    })
                                    .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                                    .show();
                        });

                        // 수정 버튼 클릭 리스너
                        btnEdit.setOnClickListener(v -> {
                            // 수정 버튼과 삭제 버튼 숨기기
                            btnEdit.setVisibility(View.GONE);
                            btnDelete.setVisibility(View.GONE);

                            // 확인 버튼 추가
                            Button btnConfirm = new Button(CalendarActivity.this);
                            btnConfirm.setText("확인");
                            LinearLayout.LayoutParams confirmButtonParams = new LinearLayout.LayoutParams(
                                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics()),
                                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics())
                            );
                            confirmButtonParams.setMargins(8, 0, 8, 0);
                            btnConfirm.setLayoutParams(confirmButtonParams);
                            btnConfirm.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
                            btnConfirm.setTextColor(Color.WHITE);
                            btnConfirm.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

                            // 취소 버튼 추가
                            Button btnCancel = new Button(CalendarActivity.this);
                            btnCancel.setText("취소");
                            LinearLayout.LayoutParams cancelButtonParams = new LinearLayout.LayoutParams(
                                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics()),
                                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 40, getResources().getDisplayMetrics())
                            );
                            cancelButtonParams.setMargins(8, 0, 8, 0);
                            btnCancel.setLayoutParams(cancelButtonParams);
                            btnCancel.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF9D1C")));
                            btnCancel.setTextColor(Color.WHITE);
                            btnCancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

                            // TextView를 EditText로 전환
                            EditText etEditContent = new EditText(CalendarActivity.this);
                            etEditContent.setText(tvContent.getText().toString());
                            etEditContent.setLayoutParams(tvContent.getLayoutParams());
                            etEditContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);

                            // 기존 상태 숨기고 새로운 상태 추가
                            tvContent.setVisibility(View.GONE);
                            ((ViewGroup) scheduleItem).addView(etEditContent, 0);
                            ((ViewGroup) scheduleItem).addView(btnConfirm);
                            ((ViewGroup) scheduleItem).addView(btnCancel);

                            // 확인 버튼 클릭 리스너
                            btnConfirm.setOnClickListener(confirmView -> {
                                String updatedContent = etEditContent.getText().toString().trim();
                                if (!updatedContent.isEmpty()) {
                                    // Firebase Realtime Database 업데이트
                                    DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                                            .getReference("schedule")
                                            .child(uid)
                                            .child(dateKey)
                                            .child(scheduleKey)
                                            .child("content");

                                    scheduleRef.setValue(updatedContent)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(CalendarActivity.this, "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show();
                                                tvContent.setText(updatedContent);

                                                // 원래 상태로 복원
                                                ((ViewGroup) scheduleItem).removeView(etEditContent);
                                                ((ViewGroup) scheduleItem).removeView(btnConfirm);
                                                ((ViewGroup) scheduleItem).removeView(btnCancel);
                                                tvContent.setVisibility(View.VISIBLE);
                                                btnEdit.setVisibility(View.VISIBLE);
                                                btnDelete.setVisibility(View.VISIBLE);
                                                updateCalendar();
                                            })
                                            .addOnFailureListener(e -> {
                                                Toast.makeText(CalendarActivity.this, "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                                            });
                                } else {
                                    Toast.makeText(CalendarActivity.this, "내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
                                }
                            });

                            // 취소 버튼 클릭 리스너
                            btnCancel.setOnClickListener(cancelView -> {
                                // 원래 상태로 복원
                                ((ViewGroup) scheduleItem).removeView(etEditContent);
                                ((ViewGroup) scheduleItem).removeView(btnConfirm);
                                ((ViewGroup) scheduleItem).removeView(btnCancel);
                                tvContent.setVisibility(View.VISIBLE);
                                btnEdit.setVisibility(View.VISIBLE);
                                btnDelete.setVisibility(View.VISIBLE);
                            });
                        });

                        scheduleListContainer.addView(scheduleItem);
                    }
                }


                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Toast.makeText(CalendarActivity.this, "일정을 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                }
            });
        };

        // 선호된 날짜를 "YYYY-MM-DD" 형식으로 변환
        String selectedDate = String.format("%d-%02d-%02d", currentYear, currentMonth + 1, day);

        // Firebase Realtime Database에서 즐겨찾기한 채용공고 가져오기
        DatabaseReference favoritesRef = FirebaseDatabase.getInstance()
                .getReference("favorites")
                .child(uid);

        favoritesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                jobListContainer.removeAllViews(); // 기존 데이터 초기화
                boolean hasJobs = false;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Job job = snapshot.getValue(Job.class);

                    if (job != null && job.getEndDate() != null && selectedDate.equals(job.getEndDate())) {
                        // 채용공고가 있으면 관련 View를 표시
                        if (!hasJobs) {
                            divider.setVisibility(View.VISIBLE);
                            tvJobHeader.setVisibility(View.VISIBLE);
                            hasJobs = true;
                        }

                        // item_job 레이아웃 추가
                        View jobItem = getLayoutInflater().inflate(R.layout.item_job, null);

                        // job 데이터 초기화
                        ImageView starIcon = jobItem.findViewById(R.id.ivStarIcon);
                        TextView tvJobTitle = jobItem.findViewById(R.id.tvJobTitle);
                        TextView tvJobLink = jobItem.findViewById(R.id.tvJobLink);

                        tvJobTitle.setText(String.format("%s", job.getCompanyName()));
                        tvJobLink.setText("공고 페이지 >");
                        tvJobLink.setOnClickListener(v -> {
                            String jobSiteUrl = job.getJobSite();
                            if (jobSiteUrl != null && !jobSiteUrl.isEmpty()) {
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(jobSiteUrl));
                                CalendarActivity.this.startActivity(browserIntent);
                            } else {
                                Toast.makeText(CalendarActivity.this, "유효한 채용공고 링크가 없습니다.", Toast.LENGTH_SHORT).show();
                            }
                        });

                        // 즐겨찾기 삭제 버튼 동작
                        starIcon.setOnClickListener(v -> {
                            favoritesRef.child(String.valueOf(job.getJobNumber())).removeValue((error, ref) -> {
                                if (error == null) {
                                    jobListContainer.removeView(jobItem); // UI에서 제거
                                    if (jobListContainer.getChildCount() == 0) {
                                        divider.setVisibility(View.GONE);
                                        tvJobHeader.setVisibility(View.GONE);
                                    }
                                    Toast.makeText(CalendarActivity.this, "즐겨찾기에서 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(CalendarActivity.this, "즐겨찾기를 삭제하는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                                    Log.e("CalendarActivity", "Error removing favorite: " + error.getMessage());
                                }
                            });
                        });

                        jobListContainer.addView(jobItem);
                    }
                }

                // 채용공고가 없는 경우 관련 View 숨김
                if (!hasJobs) {
                    divider.setVisibility(View.GONE);
                    tvJobHeader.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CalendarActivity.this, "데이터를 가져오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                Log.e("Debug", "Database error: " + databaseError.getMessage());
            }
        });

        // 초기 일정 목록 로드
        updateScheduleList.run();

        // 일정 추가 버튼 리스너
        btnAddSchedule.setOnClickListener(v -> {
            Calendar selectedDateInDialog = Calendar.getInstance();
            selectedDateInDialog.set(currentYear, currentMonth, day);
            showAddScheduleDialog(selectedDateInDialog, updateScheduleList);
        });

        dialog.show();
    }


    // 일정 추가 dialog
    private void showAddScheduleDialog(Calendar initialDate, Runnable onScheduleAdded) {
        Dialog dialog = new Dialog(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_schedule, null);
        dialog.setContentView(dialogView);

        // 다이얼로그 크기 설정
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // View 초기화
        TextView tvDialogDate = dialogView.findViewById(R.id.tvDialogDate);
        EditText etScheduleContent = dialogView.findViewById(R.id.etScheduleContent);
        Button btnRegister = dialogView.findViewById(R.id.btnRegister);
        ImageButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        ImageButton btnPrevDate = dialogView.findViewById(R.id.btnPrevDate);
        ImageButton btnNextDate = dialogView.findViewById(R.id.btnNextDate);

        // 전달받은 날짜로 초기 설정
        final Calendar selectedDate = (initialDate != null) ? (Calendar) initialDate.clone() : Calendar.getInstance();
        updateDialogDate(tvDialogDate, selectedDate);

        // 이전 날짜 버튼
        btnPrevDate.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            updateDialogDate(tvDialogDate, selectedDate);
        });

        // 다음 날짜 버튼
        btnNextDate.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            updateDialogDate(tvDialogDate, selectedDate);
        });

        // 날짜 클릭 시 DatePickerDialog 호출
        tvDialogDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(year, month, dayOfMonth);
                        updateDialogDate(tvDialogDate, selectedDate);
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        // 등록 버튼 리스너
        btnRegister.setOnClickListener(v -> {
            String content = etScheduleContent.getText().toString();
            if (!content.isEmpty()) {
                saveScheduleToRealtimeDatabase(
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH) + 1,
                        selectedDate.get(Calendar.DAY_OF_MONTH),
                        content,
                        onScheduleAdded
                );
                dialog.dismiss();
            } else {
                Toast.makeText(this, "일정 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // 취소 버튼 리스너
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }


    // 일정 저장 메서드
    private void saveScheduleToRealtimeDatabase(int year, int month, int day, String content, Runnable onScheduleAdded) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        String dateKey = createValidDateKey(year, month, day); // 유효한 키 생성

        DatabaseReference scheduleRef = FirebaseDatabase.getInstance()
                .getReference("schedule")
                .child(uid)
                .child(dateKey);

        // 가장 높은 order 값을 찾아 새로운 order 할당
        scheduleRef.orderByChild("order").limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                long maxOrder = 0;

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long order = snapshot.child("order").getValue(Long.class);
                    if (order != null) {
                        maxOrder = Math.max(maxOrder, order);
                    }
                }

                long newOrder = maxOrder + 1; // 새로운 order 값
                String newKey = scheduleRef.push().getKey(); // Firebase 고유 키 생성

                if (newKey != null) {
                    Map<String, Object> schedule = new HashMap<>();
                    schedule.put("order", newOrder);
                    schedule.put("content", content);

                    // Firebase에 새 일정 저장
                    scheduleRef.child(newKey).setValue(schedule)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(CalendarActivity.this, "일정이 등록되었습니다.", Toast.LENGTH_SHORT).show();
                                updateCalendar(); // 캘린더 즉시 새로고침
                                if (onScheduleAdded != null) {
                                    onScheduleAdded.run(); // 다이얼로그 내 일정 목록 새로고침
                                }
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(CalendarActivity.this, "일정 등록에 실패했습니다.", Toast.LENGTH_SHORT).show();
                            });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(CalendarActivity.this, "데이터베이스 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private String createValidDateKey(int year, int month, int day) {
        return String.format("%d_%02d_%02d", year, month, day);
    }


    private void updateDialogDate(TextView tvDate, Calendar date) {
        String formattedDate = String.format("%d년 %02d월 %02d일",
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH));
        tvDate.setText(formattedDate);
    }

}
