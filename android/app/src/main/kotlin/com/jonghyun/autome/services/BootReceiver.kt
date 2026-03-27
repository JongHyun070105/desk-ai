package com.jonghyun.autome.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BootReceiver: 기기 부팅 시 자동 실행 Receiver
 *
 * 기획서 요구사항:
 * - 기기 부팅 시 자동 실행 (Background Data Aggregator)
 *
 * 참고: AccessibilityService와 NotificationListenerService는
 *       시스템이 자동으로 관리하므로 별도로 시작할 필요 없음.
 *       이 Receiver는 부팅 인식 로깅 및 향후 초기화 로직을 위해 사용됨.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DaeChungTok"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed - 대충톡 services will be managed by system.")
            Log.d(TAG, "AccessibilityService: 시스템이 자동 복원 (사용자가 활성화한 경우)")
            Log.d(TAG, "NotificationListenerService: 시스템이 자동 복원 (사용자가 활성화한 경우)")

            // 향후 추가 초기화 로직 (예: 데이터 정리, 설정 로드 등)을 여기에 배치
        }
    }
}
