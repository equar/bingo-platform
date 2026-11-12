import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
    let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "ExternalLinkHandler")
    let channel = FlutterMethodChannel(name: "bingo_app/external_links", binaryMessenger: registrar.messenger())
    channel.setMethodCallHandler { call, result in
      guard call.method == "open", let value = call.arguments as? String,
            let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
        result(FlutterError(code: "invalid_url", message: "Only HTTP and HTTPS links can be opened.", details: nil))
        return
      }
      UIApplication.shared.open(url, options: [:]) { opened in
        if opened { result(nil) }
        else { result(FlutterError(code: "open_failed", message: "Unable to open the link.", details: nil)) }
      }
    }
  }
}
