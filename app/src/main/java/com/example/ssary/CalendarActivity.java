package com.example.ssary;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.text.LineBreaker;
import android.os.Bundle;
import android.text.Layout;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
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
        btnAddSchedule.setOnClickListener(v -> showAddScheduleDialog());
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
        db.collection("schedule")
                .whereEqualTo("year", currentYear)
                .whereEqualTo("month", currentMonth + 1)
                .whereEqualTo("day", day)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int maxVisibleSchedules = 4; // 최대 표시 개수
                    int barCount = 0;

                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        if (barCount >= maxVisibleSchedules) break;

                        String content = doc.getString("content");

                        TextView scheduleBar = new TextView(this);
                        scheduleBar.setText(content);
                        scheduleBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        scheduleBar.setPadding(4, 2, 4, 2);
                        scheduleBar.setBackgroundColor(Color.parseColor("#448AFF")); // 파란색 배경
                        scheduleBar.setTextColor(Color.WHITE);

                        // 텍스트가 바의 "왼쪽"부터 출력되도록 Gravity 설정
                        scheduleBar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);

                        // 텍스트 속성 추가
                        scheduleBar.setMaxLines(1); // 한 줄만 표시
                        scheduleBar.setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE); // 가능한 많은 글자를 표시
                        scheduleBar.setEllipsize(TextUtils.TruncateAt.END); // 글자가 길면 마지막에 "..." 추가
                        scheduleBar.setSingleLine(true); // 단일 라인으로 고정

                        // 바 레이아웃 파라미터 설정
                        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,  // 바의 너비
                                48 // 바의 높이 (필요에 따라 조절 가능)
                        );
                        barParams.setMargins(0, 4, 0, 0); // 바 간격
                        scheduleBar.setLayoutParams(barParams);

                        dayCell.addView(scheduleBar);
                        barCount++;
                    }

                    // 추가 일정이 있을 경우 "..." 표시
                    if (queryDocumentSnapshots.size() > maxVisibleSchedules) {
                        TextView moreText = new TextView(this);
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
                });
    }


    private void showScheduleDialog(int day) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_schedule_list);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView tvSelectedDate = dialog.findViewById(R.id.tvSelectedDate);
        LinearLayout scheduleListContainer = dialog.findViewById(R.id.scheduleListContainer);
        ImageButton btnAddSchedule = dialog.findViewById(R.id.btnAddSchedule);

        tvSelectedDate.setText(String.format("%d년 %02d월 %02d일", currentYear, currentMonth + 1, day));

        db.collection("schedule")
                .whereEqualTo("year", currentYear)
                .whereEqualTo("month", currentMonth + 1)
                .whereEqualTo("day", day)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    scheduleListContainer.removeAllViews();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        View scheduleItem = getLayoutInflater().inflate(R.layout.item_schedule, null);
                        TextView tvContent = scheduleItem.findViewById(R.id.tvScheduleContent);
                        tvContent.setText(doc.getString("content"));

                        scheduleListContainer.addView(scheduleItem);
                    }
                });

        btnAddSchedule.setOnClickListener(v -> showAddScheduleDialog());

        dialog.show();
    }

    private void showAddScheduleDialog() {
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

        // 현재 날짜 설정
        final Calendar selectedDate = Calendar.getInstance();
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
                saveScheduleToFirestore(
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH) + 1,
                        selectedDate.get(Calendar.DAY_OF_MONTH),
                        content
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


    private void saveScheduleToFirestore(int year, int month, int day, String content) {
        Map<String, Object> schedule = new HashMap<>();
        schedule.put("year", year);
        schedule.put("month", month);
        schedule.put("day", day);
        schedule.put("content", content);

        db.collection("schedule")
                .add(schedule)
                .addOnSuccessListener(docRef -> Toast.makeText(this, "일정이 등록되었습니다.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "일정 등록에 실패했습니다.", Toast.LENGTH_SHORT).show());
    }

    private void updateDialogDate(TextView tvDate, Calendar date) {
        String formattedDate = String.format("%d년 %02d월 %02d일",
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH));
        tvDate.setText(formattedDate);
    }

}
