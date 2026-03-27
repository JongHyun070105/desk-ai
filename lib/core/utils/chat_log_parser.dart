/// 대충톡 카카오톡 대화 로그 파서 (MainActivity.kt의 Dart 포팅)
///
/// Android 네이티브 parseAndInsertChatLog()와 동일한 정규표현식을 사용하여
/// Dart 환경에서 테스트 가능하도록 포팅한 클래스입니다.

class ParsedMessage {
  final String roomId;
  final String sender;
  final String message;
  final int timestamp;
  final bool isSentByMe;

  ParsedMessage({
    required this.roomId,
    required this.sender,
    required this.message,
    required this.timestamp,
    required this.isSentByMe,
  });
}

class ChatLogParser {
  static final _dateRegex = RegExp(r'-+ (\d{4})년 (\d{1,2})월 (\d{1,2})일 .+ -+');
  static final _messageRegex = RegExp(r'^\[(.+?)\] \[(.+?)\] (.+)$');

  /// 카카오톡 대화 로그 텍스트를 파싱하여 메시지 목록을 반환합니다.
  static List<ParsedMessage> parse(String content, String source) {
    final messages = <ParsedMessage>[];
    var currentSender = '';
    final currentMessage = StringBuffer();
    var currentTimestamp = DateTime.now().millisecondsSinceEpoch - 100000000;

    for (final line in content.split('\n')) {
      if (_dateRegex.hasMatch(line)) continue;

      final match = _messageRegex.firstMatch(line);
      if (match != null) {
        if (currentSender.isNotEmpty) {
          messages.add(ParsedMessage(
            roomId: source,
            sender: currentSender,
            message: currentMessage.toString().trimRight(),
            timestamp: currentTimestamp++,
            isSentByMe: currentSender == '나' || currentSender == '회원님',
          ));
          currentMessage.clear();
        }
        currentSender = match.group(1)!;
        currentMessage.write(match.group(3)!);
      } else if (line.trim().isNotEmpty && currentSender.isNotEmpty) {
        currentMessage.write('\n$line');
      }
    }

    if (currentSender.isNotEmpty && currentMessage.isNotEmpty) {
      messages.add(ParsedMessage(
        roomId: source,
        sender: currentSender,
        message: currentMessage.toString().trimRight(),
        timestamp: currentTimestamp,
        isSentByMe: currentSender == '나' || currentSender == '회원님',
      ));
    }

    return messages;
  }
}
