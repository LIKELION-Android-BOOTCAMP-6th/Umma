package com.app.umma.presentation.chat.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.umma.core.theme.BackgroundPrimary
import com.app.umma.core.theme.BackgroundSecondary
import com.app.umma.core.theme.SpacingL
import com.app.umma.core.theme.TextPrimary
import com.app.umma.core.theme.TextSecondary
import com.app.umma.core.theme.ThemePrimary
import com.app.umma.core.ui.component.UmmaDialog
import com.app.umma.domain.model.chat.AiContentReportReasonCategory

/**
 * Chat 상단의 신고 관련 액션 묶음입니다.
 *
 * ChatScreen은 어떤 상태에서 버튼을 보여줄지만 전달하고, 버튼 색/문구 같은 화면 세부사항은
 * 이 컴포넌트가 담당해 대화 화면 본문이 운영 신고 UI 구현으로 비대해지지 않게 합니다.
 */
@Composable
fun ChatReportTopActions(
    canReportAiContent: Boolean,
    isAiContentReporting: Boolean,
    hasReportedCurrentAiContent: Boolean,
    showPromptReviewReportButton: Boolean,
    isPromptReviewReporting: Boolean,
    hasPromptReviewReported: Boolean,
    onAiContentReportClick: () -> Unit,
    onPromptReviewReportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.padding(start = 10.dp)
    ) {
        // 운영용 AI 신고는 release에서도 보일 수 있는 버튼이라 dev prompt review와 분리한다.
        ReportPillButton(
            text = when {
                isAiContentReporting -> "접수중"
                hasReportedCurrentAiContent -> "접수됨"
                else -> "AI 신고"
            },
            enabled = canReportAiContent,
            selected = hasReportedCurrentAiContent,
            onClick = onAiContentReportClick
        )

        if (showPromptReviewReportButton) {
            // 개발용 리뷰 버튼은 팀 내부 prompt tuning 용도라 운영 신고와 다른 문구를 쓴다.
            ReportPillButton(
                text = if (hasPromptReviewReported) "리뷰됨" else "리뷰",
                enabled = !isPromptReviewReporting && !hasPromptReviewReported,
                selected = hasPromptReviewReported,
                onClick = onPromptReviewReportClick
            )
        }
    }
}

@Composable
private fun ReportPillButton(
    text: String,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) ThemePrimary.copy(alpha = 0.36f) else ThemePrimary
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ThemePrimary.copy(alpha = 0.16f) else ThemePrimary,
            contentColor = if (selected) ThemePrimary else TextSecondary,
            disabledContainerColor = ThemePrimary.copy(alpha = 0.14f),
            disabledContentColor = ThemePrimary.copy(alpha = 0.72f)
        ),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Google Play 정책 대응용 AI 응답 신고 다이얼로그입니다.
 *
 * 저장 자체는 ViewModel/UseCase가 처리하므로 이 컴포넌트는 사유 선택, 선택 메모 입력,
 * 키보드 닫기 콜백 연결 같은 화면 상호작용만 담당합니다.
 */
@Composable
fun AiContentReportDialog(
    selectedReasonState: MutableState<AiContentReportReasonCategory?>,
    noteState: MutableState<String>,
    isReporting: Boolean,
    onDismissKeyboard: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (AiContentReportReasonCategory, String) -> Unit
) {
    val selectedReason = selectedReasonState.value
    val note = noteState.value

    UmmaDialog(
        title = "AI 응답 신고",
        titleColor = ThemePrimary,
        modifier = Modifier
            .padding(horizontal = SpacingL)
            .imePadding(),
        onCancel = onCancel,
        onConfirm = {
            val reason = selectedReason ?: return@UmmaDialog
            onConfirm(reason, note)
        },
        confirmText = "신고",
        dismissText = "취소",
        showCancelButton = false,
        confirmButtonColor = ThemePrimary,
        confirmEnabled = selectedReason != null && !isReporting
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingL)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // TextField 바깥 여백 탭으로 키보드를 내릴 수 있게 해 구형 IME 대응을 유지한다.
                            onDismissKeyboard()
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "부적절하거나 위험한 AI 응답을 신고할 수 있어요.\n신고된 응답과 최근 대화 일부가 검토 목적으로 저장됩니다.",
                color = TextPrimary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpacingL),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiContentReportReasonCategory.entries.forEach { reason ->
                    val isSelected = selectedReason == reason
                    Button(
                        onClick = {
                            // 단일 사유만 저장해 운영 검토자가 신고 의도를 빠르게 분류할 수 있게 한다.
                            selectedReasonState.value = reason
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ThemePrimary else BackgroundSecondary,
                            contentColor = if (isSelected) Color.White else TextPrimary
                        )
                    ) {
                        Text(
                            text = reason.displayLabel(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { value ->
                    // 운영 신고 메모는 Firestore에서 바로 읽히므로 화면에서 먼저 길이를 제한한다.
                    noteState.value = value.take(AI_CONTENT_REPORT_NOTE_MAX_LENGTH)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpacingL),
                minLines = 2,
                maxLines = 4,
                label = { Text(text = "상세 내용 (선택)") },
                placeholder = { Text(text = "어떤 점이 문제였는지 적어주세요.") },
                supportingText = {
                    Text(text = "${note.length}/$AI_CONTENT_REPORT_NOTE_MAX_LENGTH")
                },
                keyboardOptions = KeyboardOptions(
                    // 신고 메모는 줄바꿈보다 키보드를 내리는 완료 액션이 더 중요하다.
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDismissKeyboard() }),
                colors = reportTextFieldColors()
            )
        }
    }
}

/**
 * 개발용 prompt review 다이얼로그입니다.
 *
 * 운영 신고와 저장 경로가 다르므로 별도 컴포넌트로 두되, 키보드 대응과 입력 제한은
 * 같은 형태로 유지해 ChatScreen의 다이얼로그 코드 중복을 줄입니다.
 */
@Composable
fun PromptReviewReportDialog(
    reportNoteState: MutableState<String>,
    onDismissKeyboard: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val reportNote = reportNoteState.value

    UmmaDialog(
        title = "대화 신고",
        titleColor = ThemePrimary,
        modifier = Modifier
            .padding(horizontal = SpacingL)
            .imePadding(),
        onCancel = onCancel,
        onConfirm = onConfirm,
        confirmText = "신고",
        dismissText = "취소",
        showCancelButton = false,
        confirmButtonColor = ThemePrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpacingL)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 개발용 리뷰 메모도 본문 빈 영역 탭으로 키보드를 내릴 수 있게 맞춘다.
                            onDismissKeyboard()
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "대화 불편을 신고할까요?\n대화내용이 저장됩니다.\n민감한 정보가 있었다면 취소를 눌러주세요.",
                color = TextPrimary,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = reportNote,
                onValueChange = { value ->
                    // Firestore report index에서 바로 읽는 값이므로 과도한 길이는 화면에서 먼저 제한한다.
                    reportNoteState.value = value.take(PROMPT_REVIEW_REPORT_NOTE_MAX_LENGTH)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SpacingL),
                minLines = 3,
                maxLines = 5,
                label = { Text(text = "불편했던 상황") },
                placeholder = { Text(text = "예: 일본어로만 답해서 따라가기 어려웠어요.") },
                supportingText = {
                    Text(text = "${reportNote.length}/$PROMPT_REVIEW_REPORT_NOTE_MAX_LENGTH")
                },
                keyboardOptions = KeyboardOptions(
                    // 개발용 리뷰 메모도 키보드가 신고 버튼을 가릴 수 있어 완료 액션으로 닫을 수 있게 한다.
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDismissKeyboard() }),
                colors = reportTextFieldColors()
            )
        }
    }
}

@Composable
private fun reportTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = BackgroundPrimary,
    unfocusedContainerColor = BackgroundPrimary,
    focusedIndicatorColor = ThemePrimary,
    unfocusedIndicatorColor = TextPrimary.copy(alpha = 0.18f),
    focusedLabelColor = ThemePrimary,
    unfocusedLabelColor = TextPrimary.copy(alpha = 0.62f)
)

private fun AiContentReportReasonCategory.displayLabel(): String {
    // 저장값은 enum name으로 유지하고, 사용자가 읽는 문구만 화면 계층에서 번역한다.
    return when (this) {
        AiContentReportReasonCategory.HarmfulDangerous -> "위험하거나 해로운 내용"
        AiContentReportReasonCategory.HateHarassment -> "혐오·괴롭힘"
        AiContentReportReasonCategory.SexualInappropriate -> "성적이거나 부적절한 내용"
        AiContentReportReasonCategory.IllegalFraud -> "불법행위·사기"
        AiContentReportReasonCategory.SelfHarm -> "자해 관련 내용"
        AiContentReportReasonCategory.Other -> "기타"
    }
}

private const val PROMPT_REVIEW_REPORT_NOTE_MAX_LENGTH = 500
private const val AI_CONTENT_REPORT_NOTE_MAX_LENGTH = 300
