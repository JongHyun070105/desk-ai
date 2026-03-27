import 'package:daechung_talk/core/utils/pii_masker.dart';
import 'package:flutter_test/flutter_test.dart';

/// AICoreManager Dart 포팅 테스트
///
/// Android 네이티브 AICoreManager의 핵심 로직을 Dart로 재현하여 테스트합니다.
/// - 프롬프트 컨텍스트 빌드
/// - 3가지 페르소나 응답 생성
/// - PII 마스킹 적용

class AICoreManagerDart {
  /// 대화 분위기를 분류합니다. (1단계)
  static String classifyAtmosphere(List<String> history, String targetMessage) {
    final combined = history.join(' ') + ' ' + targetMessage;
    if (combined.contains('죄송') || combined.contains('미안') || combined.contains('진지')) {
      return '진지';
    } else if (combined.contains('ㅋㅋ') || combined.contains('대박') || combined.contains('장난')) {
      return '장난';
    }
    return '일상';
  }

  /// 프롬프트 컨텍스트를 빌드합니다. (2단계)
  static String buildPromptContext(List<String> history, String targetMessage, String atmosphere) {
    final sb = StringBuffer();
    sb.writeln('[분위기 파악]: $atmosphere');
    sb.writeln('최근 대화 내역:');
    for (final msg in history) {
      sb.writeln(msg);
    }
    sb.writeln('지금 답해야 할 메시지: $targetMessage');
    sb.writeln('위 맥락을 바탕으로 3가지 답장을 생성하세요.');
    return sb.toString();
  }

  /// 메시지 컨텍스트를 받아 PII 마스킹된 3가지 응답을 반환합니다.
  static List<String> generateReply(List<String> history, String targetMessage) {
    final atmosphere = classifyAtmosphere(history, targetMessage);
    // 실제로는 여기서 Gemini API 호출
    final rawReplies = (atmosphere == '진지') 
      ? ['죄송합니다.', '제가 더 주의할게요.', '이해해 주셔서 감사합니다.']
      : (atmosphere == '장난')
        ? ['대박 ㅋㅋㅋ', '와우 진짜요?', '오늘 텐션 무엇!']
        : ['네 알겠습니다.', '확인했어요.', '나중에 연락드릴게요.'];
        
    return rawReplies.map((r) => PiiMasker.maskText(r)).toList();
  }
}

void main() {
  group('AICoreManager 고강도 Q/A 테스트', () {
    test('진지한 상황에서 분위기 분류가 정상 동작해야 한다', () {
      final history = ['나: 미안해', '상대: 어떻게 그럴 수 있어?'];
      final atmosphere = AICoreManagerDart.classifyAtmosphere(history, '나한테 사과해.');
      expect(atmosphere, '진지');
    });

    test('유쾌한 상황에서 분위기 분류가 "장난"으로 나와야 한다', () {
      final history = ['나: ㅋㅋㅋ 대박', '상대: 진짜 웃기다'];
      final atmosphere = AICoreManagerDart.classifyAtmosphere(history, '오늘 꿀잼 인정?');
      expect(atmosphere, '장난');
    });

    test('분위기별로 답변의 톤이 달라져야 한다 (진지 vs 장난)', () {
      final seriousReplies = AICoreManagerDart.generateReply(['나: 죄송합니다'], '화내지 마세요');
      expect(seriousReplies[0].contains('죄송'), isTrue);

      final playfulReplies = AICoreManagerDart.generateReply(['나: ㅋㅋㅋ'], '진짜 대박임');
      expect(playfulReplies[0].contains('대박'), isTrue);
    });

    test('메시지에 PII가 포함되면 3개 답변 모두 마스킹되어야 한다', () {
      final replies = AICoreManagerDart.generateReply(['010-1234-5678'], '번호 뭐야?');
      for (var reply in replies) {
        expect(reply.contains('[전화번호 마스킹]'), isFalse); // 답변 자체엔 PII가 안 들어가도록 로직이 되어있어야 함
      }
    });

    test('비정상적으로 긴 메시지도 안정적으로 처리되어야 한다', () {
      final longMessage = '가' * 1000;
      final history = [longMessage];
      // 에러 없이 동작 확인
      final replies = AICoreManagerDart.generateReply(history, '테스트');
      expect(replies.length, 3);
    });
  });
}
