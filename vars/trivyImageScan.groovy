// vars/trivyImageScan.groovy
def call(List images, String severity = 'HIGH,CRITICAL', Boolean failBuild = false) { // Changed String[] to List
    echo "--- Starting Trivy Scan for ${images.size()} images ---"

    // Set to "0" during initial testing so your pipeline doesn't stop on vulns
    def exitCode = failBuild ? "1" : "0"

    for (image in images) {
        def reportName = "trivy-report-${image.replaceAll(/[:\/]/, '-')}.txt"

        echo "Scanning: ${image}"

        // Use sh to run the scan
        sh """
            trivy image \
                --severity ${severity} \
                --exit-code ${exitCode} \
                --format table \
                ${image} | tee ${reportName}
        """

        archiveArtifacts artifacts: reportName, allowEmptyArchive: true
    }
}