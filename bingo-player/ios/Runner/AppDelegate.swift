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
    guard let registrar = engineBridge.pluginRegistry.registrar(forPlugin: "ExternalLinkHandler") else {
      fatalError("Unable to register the external link handler.")
    }
    let channel = FlutterMethodChannel(name: "bingo_player/external_links", binaryMessenger: registrar.messenger())
    channel.setMethodCallHandler { call, result in
      if call.method == "getInstallReferralCode" {
        result(nil)
        return
      }
      if call.method == "share" {
        guard let args = call.arguments as? [String: String],
              let text = args["text"], !text.isEmpty else {
          result(FlutterError(code: "invalid_message", message: "Share text is required.", details: nil))
          return
        }
        let controller = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        if let presenter = Self.topViewController() {
          controller.popoverPresentationController?.sourceView = presenter.view
          controller.popoverPresentationController?.sourceRect = CGRect(
            x: presenter.view.bounds.midX,
            y: presenter.view.bounds.midY,
            width: 0,
            height: 0
          )
          presenter.present(controller, animated: true)
          result(nil)
        } else {
          result(FlutterError(code: "share_unavailable", message: "Unable to open the share sheet.", details: nil))
        }
        return
      }
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

  private static func topViewController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .first { $0.activationState == .foregroundActive }
    var controller = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
    while let presented = controller?.presentedViewController {
      controller = presented
    }
    return controller
  }
}
