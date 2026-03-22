import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:file_picker/file_picker.dart';

class TxtImportScreen extends StatefulWidget {
  const TxtImportScreen({super.key});

  @override
  State<TxtImportScreen> createState() => _TxtImportScreenState();
}

class _TxtImportScreenState extends State<TxtImportScreen> {
  static const MethodChannel _channel = MethodChannel('com.jonghyun.autome/native');
  bool _isLoading = false;

  Future<void> _pickAndProcessFile() async {
    try {
      setState(() => _isLoading = true);
      
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['txt'],
      );

      if (result != null && result.files.single.path != null) {
        final filePath = result.files.single.path!;
        
        await _channel.invokeMethod('processFile', {'filePath': filePath});
        
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('파일 가져오기 및 파싱 작업이 백그라운드에서 시작되었습니다.')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('오류 발생: \$e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('TXT 데이터 Import')),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Icon(Icons.upload_file, size: 64, color: Colors.blueGrey),
            const SizedBox(height: 16),
            const Text(
              '대화 내역 내보내기 파일 읽기',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            const Text(
              '카카오톡 등의 메신저에서 내보낸 텍스트(.txt) 파일을 선택하면, 앱 내부에서 자동 파싱 후 로컬 DB에 적재합니다.',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.black54),
            ),
            const SizedBox(height: 32),
            _isLoading 
              ? const Center(child: CircularProgressIndicator())
              : ElevatedButton.icon(
                  icon: const Icon(Icons.folder_open),
                  label: const Text('텍스트 파일 선택하기 (.txt)'),
                  onPressed: _pickAndProcessFile,
                ),
          ],
        ),
      ),
    );
  }
}
