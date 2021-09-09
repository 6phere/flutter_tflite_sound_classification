import 'dart:async';

import 'package:flutter/services.dart';

/// Class which manages the future and stream for the plugins
class TfliteSoundClassification {
  static const MethodChannel _channel = MethodChannel('tflite_sound_classification');
  static const EventChannel _eventChannel =
      EventChannel('startAudioRecognition');

  /// [startAudioRecognition] returns map objects with the following values:
  /// 1. String recognitionResult
  /// 2. bool hasPermission
  static Stream<Map<dynamic, dynamic>> startAudioRecognition() {
    final recognitionStream =
        _eventChannel.receiveBroadcastStream(<String, dynamic>{});

    ///cast the result of the stream a map object.
    return recognitionStream
        .cast<Map<dynamic, dynamic>>()
        .map((event) => Map<dynamic, dynamic>.from(event));
  }

  ///call [stopAudioRecognition] to forcibly stop recording, recognition and
  ///stream.
  static Future stopAudioRecognition() async {
    return _channel.invokeMethod('stopAudioRecognition');
  }

  ///initialize [loadModel] before calling any other streams and futures.
  static Future loadModel(
      {required String model,
      required String label,
      required String classificationInterval,
      int numThreads = 1,
      bool isAsset = true}) async {
    return _channel.invokeMethod(
      'loadModel',
      {
        'model': model,
        'label': label,
        'classificationInterval': classificationInterval,
        'numThreads': numThreads,
        'isAsset': isAsset,
      },
    );
  }
}
