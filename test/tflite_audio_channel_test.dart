/*
check if argument passes
https://github.com/flutter/plugins/blob/f93314bb3779ebb0151bc326a0e515ca5f46533c/packages/image_picker/image_picker_platform_interface/test/new_method_channel_image_picker_test.dart
*/

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tflite_sound_classification/tflite_sound_classification.dart';
import 'package:flutter/services.dart';

final List<MethodCall> log = <MethodCall>[];
const MethodChannel channel = MethodChannel('tflite_sound_classification');
// const MethodChannel eventChannel = MethodChannel('startAudioRecognition');

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  group('loadModel() test', () {
    setUp(() async {
      channel.setMockMethodCallHandler((methodCall) async {
        log.add(methodCall);
        return '';
      });
      log.clear();
    });

    tearDown(() {
      channel.setMockMethodCallHandler(null);
    });

    test('passes optional and required arguments correctly', () async {

      await TfliteAudio.loadModel(
        model: 'assets/google_teach_machine_model.tflite',
        label: 'assets/google_teach_machine_model.txt',
      );

      expect(
        log,
        <Matcher>[
          isMethodCall(
            'loadModel',
            arguments: <dynamic, dynamic>{
              'model': 'assets/google_teach_machine_model.tflite',
              'label': 'assets/google_teach_machine_model.txt',
              'numThreads': 1,
              'isAsset': true,
            },
          ),
        ],
      );
    });
  });

  // group('startAudioRecognition() test', () {
  //   setUp(() async {
  //     eventChannel.setMockMethodCallHandler((methodCall) async {
  //       log.add(methodCall);
  //       return '';
  //     });
  //     log.clear();
  //   });

  //   tearDown(() {
  //     eventChannel.setMockMethodCallHandler(null);
  //   });

  //   test('passes optional and required arguments correctly', () async {
  //     TfliteAudio.startAudioRecognition(
  //         inputType: 'decodedWav',
  //         sampleRate: 16000,
  //         recordingLength: 16000,
  //         bufferSize: 2000);

  //     expect(
  //       log,
  //       <Matcher>[
  //         isMethodCall(
  //           'startAudioRecognition',
  //           arguments: <dynamic, dynamic>{
  //             'inputType': 'decodedWav',
  //             'sampleRate': 16000,
  //             'recordingLength': 16000,
  //             'bufferSize': 2000,
  //           },
  //         ),
  //       ],
  //     );
  //   });
  // });
}
