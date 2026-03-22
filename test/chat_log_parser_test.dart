import 'package:flutter_test/flutter_test.dart';
import 'package:auto_me/core/utils/chat_log_parser.dart';

void main() {
  group('ChatLogParser 테스트', () {
    test('기본 단일 라인 메시지 파싱', () {
      const input = '[홍길동] [오후 3:42] 안녕하세요!';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 1);
      expect(results[0].sender, '홍길동');
      expect(results[0].message, '안녕하세요!');
      expect(results[0].roomId, 'test_room');
      expect(results[0].isSentByMe, isFalse);
    });

    test('다중 라인 메시지 파싱', () {
      const input = '[홍길동] [오후 3:42] 첫 번째 줄\n두 번째 줄\n세 번째 줄';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 1);
      expect(results[0].sender, '홍길동');
      expect(results[0].message, '첫 번째 줄\n두 번째 줄\n세 번째 줄');
    });

    test('여러 사람의 대화 파싱', () {
      const input =
          '[홍길동] [오후 3:42] 안녕하세요!\n'
          '[김영희] [오후 3:43] 네 안녕하세요~\n'
          '[나] [오후 3:44] 반갑습니다';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 3);
      expect(results[0].sender, '홍길동');
      expect(results[0].message, '안녕하세요!');
      expect(results[1].sender, '김영희');
      expect(results[1].message, '네 안녕하세요~');
      expect(results[2].sender, '나');
      expect(results[2].message, '반갑습니다');
      expect(results[2].isSentByMe, isTrue);
    });

    test('날짜 구분선은 무시해야 한다', () {
      const input =
          '--------------- 2024년 1월 15일 월요일 ---------------\n'
          '[홍길동] [오후 3:42] 안녕하세요!';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 1);
      expect(results[0].sender, '홍길동');
    });

    test('"나"와 "회원님" 발신자는 isSentByMe=true로 표시', () {
      const input =
          '[나] [오후 1:00] 내가 보낸 메시지\n'
          '[회원님] [오후 1:01] 회원님이 보낸 메시지';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 2);
      expect(results[0].isSentByMe, isTrue);
      expect(results[1].isSentByMe, isTrue);
    });

    test('빈 입력에 대해 빈 결과를 반환해야 한다', () {
      final results = ChatLogParser.parse('', 'test_room');
      expect(results, isEmpty);
    });

    test('roomId가 source 파라미터와 일치해야 한다', () {
      const input = '[테스트] [오전 10:00] 메시지';
      final results = ChatLogParser.parse(input, 'shared_file_import');

      expect(results[0].roomId, 'shared_file_import');
    });

    test('timestamp가 순차적으로 증가해야 한다', () {
      const input =
          '[A] [오후 1:00] 첫 번째\n'
          '[B] [오후 1:01] 두 번째\n'
          '[C] [오후 1:02] 세 번째';
      final results = ChatLogParser.parse(input, 'test_room');

      expect(results.length, 3);
      expect(results[0].timestamp < results[1].timestamp, isTrue);
      expect(results[1].timestamp < results[2].timestamp, isTrue);
    });
  });
}
