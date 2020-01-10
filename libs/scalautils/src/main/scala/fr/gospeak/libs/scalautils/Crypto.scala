package fr.gospeak.libs.scalautils

import java.nio.ByteBuffer
import java.security.{Key, MessageDigest, SecureRandom}
import java.util.Base64

import javax.crypto.spec.IvParameterSpec
import javax.crypto.{Cipher, KeyGenerator}

import scala.util.Try

object Crypto {
  private val charEnc = "UTF-8"
  private val aesKeyLength = 128
  private val aesTransformationString = "AES/CFB/NoPadding"

  def base64Encode(message: String): String =
    base64Encode(message.getBytes)

  def base64Encode(message: Array[Byte]): String =
    new String(Base64.getEncoder.encode(message), charEnc)

  def base64Decode(base64: String): Try[String] =
    base64DecodeBytes(base64).map(new String(_, charEnc))

  def base64DecodeBytes(base64: String): Try[Array[Byte]] =
    Try(Base64.getDecoder.decode(base64.getBytes(charEnc)))

  def md5(message: String): String =
    MessageDigest.getInstance("MD5").digest(message.getBytes).map("%02x".format(_)).mkString

  def sha1(message: String): String =
    MessageDigest.getInstance("SHA1").digest(message.getBytes).map("%02x".format(_)).mkString

  def secureRandom(): Try[Double] = {
    Try(SecureRandom.getInstance("SHA1PRNG")).map { sr =>
      val seedByteCount = 10
      val seed = sr.generateSeed(seedByteCount)
      sr.setSeed(seed)
      sr.nextDouble()
    }
  }

  final case class AesSecretKey(value: String) extends Key {
    override def getAlgorithm: String = "AES"

    override def getFormat: String = "RAW"

    override def getEncoded: Array[Byte] = base64DecodeBytes(value).get

    override def toString: String = "AesSecretKey(*****)"
  }

  final case class AesEncrypted(cipher: String)

  def aesGenerateKey(): Try[AesSecretKey] = for {
    // Generate an AES key of the desired length (in bits) using an AES KeyGenerator.
    keyGen <- Try(KeyGenerator.getInstance("AES"))
    _ <- Try(keyGen.init(aesKeyLength))
    secretKey <- Try(keyGen.generateKey)
  } yield AesSecretKey(base64Encode(secretKey.getEncoded))

  // adapted from https://www.owasp.org/index.php/Using_the_Java_Cryptographic_Extensions
  def aesEncrypt(message: String, secretKey: AesSecretKey): Try[AesEncrypted] = {
    for {
      // Get a Cipher instance of the desired algorithm, mode, and padding.
      aesCipherForEncryption <- Try(Cipher.getInstance(aesTransformationString))
      // Generate an initialization vector for our message of the same size as the Cipher's blocksize.
      iv <- Try(aesCipherForEncryption.getBlockSize).map(new Array[Byte](_))
      prng <- Try(new SecureRandom())
      _ = prng.nextBytes(iv)
      // Initialize the Cipher instance for encryption using the key and initialization vector.
      p <- Try(new IvParameterSpec(iv))
      _ <- Try(aesCipherForEncryption.init(Cipher.ENCRYPT_MODE, secretKey, p))
      // Use the Cipher to encrypt the message (after encoding it to a byte[] using the named Charset), and then append the encrypted data to the IV and Base64-encode the result.
      m <- Try(message.getBytes(charEnc))
      encrypted <- Try(aesCipherForEncryption.doFinal(m))
      cipherData = ByteBuffer.allocate(iv.length + encrypted.length)
      _ = cipherData.put(iv)
      _ = cipherData.put(encrypted)
    } yield AesEncrypted(base64Encode(cipherData.array))
  }

  def aesDecrypt(message: AesEncrypted, secretKey: AesSecretKey): Try[String] = {
    for {
      // Get a new Cipher instance of the same algorithm, mode, and padding used for encryption.
      aesCipherForDecryption <- Try(Cipher.getInstance(aesTransformationString))
      // Base64-decode and split the data into the IV and the encrypted data, and then initialize the cipher for decryption with the same key used for encryption (symmetric), the IV, and the encrypted data.
      cipherData <- base64DecodeBytes(message.cipher).map(ByteBuffer.wrap)
      iv = new Array[Byte](aesCipherForDecryption.getBlockSize)
      _ <- Try(cipherData.get(iv))
      encrypted = new Array[Byte](cipherData.remaining)
      _ <- Try(cipherData.get(encrypted))
      _ <- Try(aesCipherForDecryption.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv)))
      // Use the Cipher to decrypt the data, convert it to a String using the named Charset, and display the message.
      decrypted <- Try(aesCipherForDecryption.doFinal(encrypted))
    } yield new String(decrypted, charEnc)
  }
}
