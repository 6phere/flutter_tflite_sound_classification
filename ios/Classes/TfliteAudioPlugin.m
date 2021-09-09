#import "TfliteAudioPlugin.h"
#if __has_include(<tflite_sound_classification/tflite_sound_classification-Swift.h>)
#import <tflite_sound_classification/tflite_sound_classification-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "tflite_sound_classification-Swift.h"
#endif

@implementation TfliteAudioPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftTfliteAudioPlugin registerWithRegistrar:registrar];
}
@end
