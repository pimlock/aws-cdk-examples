package software.amazon.awscdk.examples;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroup;
import software.amazon.awscdk.services.autoscaling.AutoScalingGroupProps;
import software.amazon.awscdk.services.autoscaling.CfnLaunchConfiguration;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.amazon.awscdk.services.s3.CfnBucket;

import java.util.Collections;

/**
 * This is an example of how to override properties of underlying CloudFormation resource of
 * high-level CDK construct.
 *
 * Note: this is just a reference code to show examples of how to use L1 resources.
 * Running `cdk deploy` on this app will fail, however you can still run `cdk synth` and explore
 * CloudFormation template that gets generated.
 */
class ResourceOverridesStack extends Stack {
    public ResourceOverridesStack(final Construct parent, final String name) {
        super(parent, name);

        Bucket otherBucket = new Bucket(this, "Other");

        Bucket bucket = new Bucket(this, "MyBucket", BucketProps.builder()
                .versioned(true)
                .encryption(BucketEncryption.KMS_MANAGED)
                .build()
        );
        CfnBucket bucketResource = (CfnBucket) bucket.getNode().getDefaultChild();

        //
        // This is how to access L1 construct
        //
        accessCfnBucketExample(bucket);

        //
        // This is how to modify properties of L1 construct
        //
        modifyPropertiesExample(bucket);

        //
        // This is how to specify resource options such as dependencies, metadata, update policy
        //
        bucketResource.getNode().addDependency(otherBucket.getNode().getDefaultChild());
        bucketResource.getCfnOptions().setMetadata(
                ImmutableMap.of("MetadataKey", "MetadataValue")
        );
        bucketResource.getCfnOptions().setUpdatePolicy(
                CfnUpdatePolicy.builder()
                        .autoScalingRollingUpdate(CfnAutoScalingRollingUpdate.builder().pauseTime("390").build())
                        .build()
        );

        //
        // This is how to specify "raw" overrides at the __resource__ level
        //
        bucketResource.addOverride("Type", "AWS::S3::Bucketeer"); // even "Type" can be overridden
        bucketResource.addOverride("Transform", "Boom");
        bucketResource.addOverride("Properties.CorsConfiguration",
                ImmutableMap.builder()
                        .put("Custom", 123)
                        .put("Bar", ImmutableList.of("A", "B"))
                        .build()
        );

        // addPropertyOverride simply allows you to omit the "Properties." prefix
        bucketResource.addPropertyOverride("VersioningConfiguration.Status", "NewStatus");
        bucketResource.addPropertyOverride("Token", otherBucket.getBucketArn());
        // it's possible to mix L1 and L2 constructs - in this case otherBucket.getBucketName() will create "Ref:" in CloudFormation template
        bucketResource.addPropertyOverride("LoggingConfiguration.DestinationBucketName", otherBucket.getBucketName());

        bucketResource.setAnalyticsConfigurations(Collections.singletonList(ImmutableMap.builder()
                .put("id", "config1")
                .put("storageClassAnalysis", ImmutableMap.of(
                        "dataExport", ImmutableMap.builder()
                                .put("outputSchemaVersion", "1")
                                .put("destination", ImmutableMap.builder()
                                        .put("format", "html")
                                        // using L2 construct's method will work as expected
                                        .put("bucketArn", otherBucket.getBucketArn())
                                        .build()
                                )
                                .build()
                        )
                )
                .build()
        ));

        //
        // It is also possible to request a deletion of a value by either assigning
        // `null` or use the `addDeletionOverride` method
        //
        bucketResource.addDeletionOverride("Metadata");
        // same as above
        bucketResource.addOverride("Metadata", null);
        bucketResource.addPropertyDeletionOverride("CorsConfiguration.Bar");

        Vpc vpc = new Vpc(this, "VPC", VpcProps.builder().maxAzs(1).build());
        AutoScalingGroup asg = new AutoScalingGroup(this, "ASG", AutoScalingGroupProps.builder()
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.MEMORY4, InstanceSize.XLARGE))
                .machineImage(new AmazonLinuxImage())
                .build()
        );

        //
        // The default child resource is called `Resource`, but secondary resources, such as
        // an LaunchConfig, InstanceRole will have a different ID.
        // See https://docs.aws.amazon.com/cdk/api/latest/docs/@aws-cdk_core.ConstructNode.html#defaultchild
        //
        CfnLaunchConfiguration launchConfiguration = (CfnLaunchConfiguration) asg.getNode().findChild("LaunchConfig");
        launchConfiguration.addPropertyOverride("Foo.Bar", "Hello");
    }

    /**
     * Example of accessing L1 bucket resource from L2 bucket construct.
     * <p>
     * You can read more on L1 vs L2 constructs here: https://aws.amazon.com/blogs/developer/contributing-to-the-aws-cloud-development-kit/
     */
    private void accessCfnBucketExample(Bucket bucket) {
        // accessing through finding a child of specific type (not pretty in Java)
        CfnBucket bucketResource1 = (CfnBucket) bucket.getNode().getChildren()
                .stream()
                .filter(child -> ((CfnResource) child).getCfnResourceType().equals("AWS::S3::Bucket"))
                .findFirst()
                .get();

        // accessing through getting a default child
        CfnBucket bucketResource2 = (CfnBucket) bucket.getNode().getDefaultChild();

        assert bucketResource1.equals(bucketResource2);
    }

    /**
     * Example of how properties of CloudFormation resource can be modified.
     * Paths for the properties can be found in CloudFormation documentation.
     * For S3 bucket properties see: https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-s3-bucket.html
     */
    private void modifyPropertiesExample(Bucket bucket) {
        CfnBucket bucketCfnResource = (CfnBucket) bucket.getNode().getDefaultChild();

        // This is an invalid CF property, but there is no validation at this point, so anything can be set.
        // This is just to show that anything can be set at this point, but it's only validated ones the stack
        // is being deployed to CloudFormation.
        bucketCfnResource.addPropertyOverride("BucketEncryption.ServerSideEncryptionConfiguration.0.EncryptEverythingAndAlways", true);

        // This is a valid CF property
        bucketCfnResource.addPropertyDeletionOverride("BucketEncryption.ServerSideEncryptionConfiguration.0.ServerSideEncryptionByDefault");
    }
}
