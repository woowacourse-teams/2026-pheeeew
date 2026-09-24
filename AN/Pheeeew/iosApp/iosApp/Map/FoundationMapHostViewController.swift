import Shared
import UIKit

final class FoundationMapHostViewController: UIViewController {
    private let renderer: FoundationMapRenderer

    init(eventSink: FoundationIosMapEventSink) {
        renderer = FoundationMapRenderer(eventSink: eventSink)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func loadView() {
        view = renderer.mapView
    }

    func update(state: FoundationIosMapRenderUiModel) {
        renderer.update(state: state)
    }

    func releaseResources() {
        renderer.releaseResources()
    }
}

private final class FoundationMapContainerView: UIView {
    private let hostController: FoundationMapHostViewController

    init(eventSink: FoundationIosMapEventSink) {
        hostController = FoundationMapHostViewController(eventSink: eventSink)
        super.init(frame: .zero)
        addSubview(hostController.view)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) is not supported")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        hostController.view.frame = bounds
    }

    func update(state: FoundationIosMapRenderUiModel) {
        hostController.update(state: state)
    }

    func releaseResources() {
        hostController.releaseResources()
    }
}

final class FoundationMapFactory: NSObject, FoundationIosMapFactory {
    func createMapView(eventSink: FoundationIosMapEventSink) -> UIView {
        FoundationMapContainerView(eventSink: eventSink)
    }

    func updateMapView(mapView: UIView, state: FoundationIosMapRenderUiModel) {
        (mapView as? FoundationMapContainerView)?.update(state: state)
    }

    func releaseMapView(mapView: UIView) {
        (mapView as? FoundationMapContainerView)?.releaseResources()
    }
}
