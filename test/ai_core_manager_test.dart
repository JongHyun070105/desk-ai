import 'package:flutter_test/flutter_test.dart';
import 'package:auto_me/core/utils/pii_masker.dart';

/// AICoreManager Dart 포팅 테스트
///
/// Android 네이티브 AICoreManager의 핵심 로직을 Dart로 재현하여 테스트합니다.
/// - 프롬프트 컨텍스트 빌드
/// - 3가지 페르소나 응답 생성
/// - PII 마스킹 적용

class AICoreManagerDart {
  /// 프롬프트 컨텍스트를 빌드합니다.
  static String buildPromptContext(List<String> messageContext) {
    final sb = StringBuffer();
    sb.writeln('다음은 최근 대화 내역입니다:');
    sb.writeln('---');
    for (final msg in messageContext) {
      sb.writeln(msg);
    }
    sb.writeln('---');
    sb.writeln('위 맥락을 바탕으로, 자연스러운 답장을 3가지 톤(수락, 거절, 모호함)으로 생성하세요.');
    return sb.toString();
  }

  /// Fallback 템플릿 응답 생성 (AICore SDK 미배포 시)
  static List<String> generateFallbackReplies() {
    return [
      '네, 알겠습니다! 확인했어요.',
      '죄송한데 지금은 어려울 것 같아요.',
      '음, 조금 더 생각해볼게요.',
    ];
  }

  /// 메시지 컨텍스트를 받아 PII 마스킹된 3가지 응답을 반환합니다.
  static List<String> generateReply(List<String> messageContext) {
    final rawReplies = generateFallbackReplies();
    return rawReplies.map((r) => PiiMasker.maskText(r)).toList();
  }
}

void main() {
  group('AICoreManager 테스트', () {
    test('프롬프트 컨텍스트 빌드가 정상 동작해야 한다', () {
      final context = ['나: 안녕하세요', '홍길동: 반갑습니다', '나: 오늘 시간 되세요?'];
      final prompt = AICoreManagerDart.buildPromptContext(context);

      expect(prompt.contains('다음은 최근 대화 내역입니다:'), isTrue);
      expect(prompt.contains('나: 안녕하세요'), isTrue);
      expect(prompt.contains('홍길동: 반갑습니다'), isTrue);
      expect(prompt.contains('나: 오늘 시간 되세요?'), isTrue);
      expect(prompt.contains('3가지 톤(수락, 거절, 모호함)'), isTrue);
    });

    test('Fallback 응답이 정확히 3개여야 한다', () {
      final replies = AICoreManagerDart.generateFallbackReplies();
      expect(replies.length, 3);
    });

    test('3가지 페르소나(수락, 거절, 모호함)가 모두 구분되어야 한다', () {
      final replies = AICoreManagerDart.generateFallbackReplies();

      // 수락 페르소나: 긍정적 키워드
      expect(replies[0].contains('알겠') || replies[0].contains('확인'), isTrue);
      // 거절 페르소나: 부정적 키워드
      expect(replies[1].contains('어려울') || replies[1].contains('죄송'), isTrue);
      // 모호함 페르소나: 유보적 키워드
      expect(replies[2].contains('생각해볼') || replies[2].contains('글쎄'), isTrue);
    });

    test('생성된 응답에 PII가 포함되어 있으면 마스킹되어야 한다', () {
      // PII가 포함된 응답을 시뮬레이션
      final rawReply = '제 번호는 010-1234-5678이에요.';
      final masked = PiiMasker.maskText(rawReply);

      expect(masked.contains('010-1234-5678'), isFalse);
      expect(masked.contains('[전화번호 마스킹]'), isTrue);
    });

    test('generateReply가 PII 마스킹된 3개 응답을 반환해야 한다', () {
      final context = ['나: 안녕하세요'];
      final replies = AICoreManagerDart.generateReply(context);

      expect(replies.length, 3);
      // Fallback 응답에는 PII가 없으므로 원본 그대로 나와야 함
      expect(replies[0], '네, 알겠습니다! 확인했어요.');
      expect(replies[1], '죄송한데 지금은 어려울 것 같아요.');
      expect(replies[2], '음, 조금 더 생각해볼게요.');
    });

    test('빈 컨텍스트에서도 프롬프트 빌드가 동작해야 한다', () {
      final prompt = AICoreManagerDart.buildPromptContext([]);
      expect(prompt.contains('다음은 최근 대화 내역입니다:'), isTrue);
      expect(prompt.contains('---'), isTrue);
    });
  });
}
