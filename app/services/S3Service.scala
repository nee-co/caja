package services

import java.io.File

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.util.IOUtils
import com.typesafe.config.ConfigFactory
import utils.Using

import scala.util.{Failure, Success, Try}

class S3Service {
  private val config = ConfigFactory.parseFile(new File("./conf/application.conf")).resolve
  private val accessKey  = config.getString("aws.s3.accesskey")
  private val secretKey  = config.getString("aws.s3.secretkey")
  private val bucketName = config.getString("aws.s3.bucketname")
  private val s3 = new AmazonS3Client(new BasicAWSCredentials(accessKey, secretKey))

  def upload(objectKey: String, file: File): Boolean = {

    Try(s3.putObject(bucketName, objectKey, file)) match {
      case Success(result) => hasObject(objectKey)
      case Failure(t) => false
    }
  }

  def download(objectKey: String): Option[Array[Byte]] = {
    val obj = Try(s3.getObject(bucketName, objectKey))

    obj match {
      case Success(result) => for (in <- Using(obj.get.getObjectContent)) Some(IOUtils.toByteArray(in))
      case Failure(t) => None
    }
  }

  def delete(objectKey: String): Boolean = {
    Try(s3.deleteObject(bucketName, objectKey)) match {
      case Success(result) => !hasObject(objectKey)
      case Failure(t) => false
    }
  }

  def hasObject(objectKey: String): Boolean = s3.doesObjectExist(bucketName, objectKey)
}
