package fr.vueconfort.app.precompensation

import kotlin.math.*

/** Debug-only numerical reference. It is deliberately independent from Release profiles. */
const val OPTICAL_MODEL_V2 = "OPTICAL_MODEL_V2"

enum class OpticalInputSource { PRESCRIPTION, PERCEPTUAL_ESTIMATE, WAVEFRONT_MEASUREMENT, HYBRID }
enum class ParameterOrigin { MEASURED, PRESCRIPTION, USER_REPORTED, ANDROID_LOGICAL, ASSUMED, UNKNOWN }
enum class BinocularOptimizationStrategy { RIGHT_EYE, LEFT_EYE, DOMINANT_EYE, BINOCULAR_COMPROMISE }
enum class RangeMethod { HARD_CLIP, SOFT_CLIP, RANGE_COMPRESSION }
enum class ValidationStatus { CALCULATED, EXPERIMENTAL, VALIDATED_PERCEPTUALLY, INCONCLUSIVE, REJECTED }

data class Spherocylinder(val sphereD: Double, val cylinderD: Double, val axisDegrees: Double) {
    init { require(sphereD.isFinite() && cylinderD.isFinite() && axisDegrees.isFinite()) }
    fun minusCylinder(): Spherocylinder = if (cylinderD <= 0.0) copy(axisDegrees = axisDegrees.mod(180.0))
        else Spherocylinder(sphereD + cylinderD, -cylinderD, (axisDegrees + 90.0).mod(180.0))
}

data class PowerVector(val meanD: Double, val j0D: Double, val j45D: Double)

object PrescriptionNormalizer {
    /** Thibos minus-cylinder convention: M=S+C/2, J0=-C cos(2a)/2, J45=-C sin(2a)/2. */
    fun powerVector(value: Spherocylinder): PowerVector {
        val v = value.minusCylinder(); val a = Math.toRadians(v.axisDegrees)
        return PowerVector(v.sphereD + v.cylinderD / 2.0, -v.cylinderD * cos(2.0 * a) / 2.0,
            -v.cylinderD * sin(2.0 * a) / 2.0)
    }
}

data class PupilModel(val diameterMm: Double, val origin: ParameterOrigin) {
    init { require(diameterMm in 1.0..9.0) }
    val radiusMeters = diameterMm / 2000.0
}

data class DisplayModel(
    val widthPx: Int, val heightPx: Int, val densityDpi: Double, val densityOrigin: ParameterOrigin,
    val viewingDistanceCm: Double, val wavelengthNm: Double = 555.0, val colorSpace: String = "sRGB→linear luminance"
) {
    init { require(widthPx > 0 && heightPx > 0 && densityDpi > 0 && viewingDistanceCm > 0 && wavelengthNm in 380.0..780.0) }
    val estimatedPixelPitchMm = 25.4 / densityDpi
    val pixelsPerDegree = densityDpi / 25.4 * (2.0 * viewingDistanceCm * 10.0 * tan(Math.toRadians(0.5)))
    val nyquistCyclesPerDegree = pixelsPerDegree / 2.0
}

/** OSA/ANSI normalized modes: Z20=sqrt(3)(2r²-1), Z22c/s=sqrt(6)r² cos/sin(2θ). */
data class LowOrderWavefront(val c20Meters: Double, val c22CosMeters: Double, val c22SinMeters: Double,
    val convention: String = "OSA/ANSI normalized; pupil coordinates; OPD metres; exp(+i2πW/λ)")

object WavefrontModel {
    /**
     * Paraxial thin-lens OPD convention:
     * W(x,y)=-1/2[M(x²+y²)+J0(x²-y²)+2J45xy].
     * This is LOW_ORDER_ONLY: no higher-order aberration is inferred from a prescription.
     */
    fun fromPrescription(power: PowerVector, pupil: PupilModel): LowOrderWavefront {
        val a2 = pupil.radiusMeters.pow(2)
        return LowOrderWavefront(-a2 * power.meanD / (4.0 * sqrt(3.0)),
            -a2 * power.j0D / (2.0 * sqrt(6.0)), -a2 * power.j45D / (2.0 * sqrt(6.0)))
    }
    fun valueMeters(w: LowOrderWavefront, rho: Double, theta: Double): Double =
        w.c20Meters * sqrt(3.0) * (2.0 * rho * rho - 1.0) +
            w.c22CosMeters * sqrt(6.0) * rho * rho * cos(2.0 * theta) +
            w.c22SinMeters * sqrt(6.0) * rho * rho * sin(2.0 * theta)
}

data class ComplexGrid(val size: Int, val real: DoubleArray, val imag: DoubleArray) {
    init { require(real.size == size * size && imag.size == real.size) }
}
data class PsfResult(val size: Int, val values: DoubleArray, val wavefront: LowOrderWavefront, val pupil: PupilModel,
    val wavelengthNm: Double) {
    val sum get() = values.sum()
}
data class OtfResult(val size: Int, val real: DoubleArray, val imag: DoubleArray) {
    fun mtf() = DoubleArray(real.size) { hypot(real[it], imag[it]) }
}

object FourierReferenceV2 {
    fun transform2d(inputRe: DoubleArray, inputIm: DoubleArray, n: Int, inverse: Boolean): ComplexGrid {
        require(inputRe.size == n * n && inputIm.size == inputRe.size)
        if (n > 0 && n and (n - 1) == 0) return fft2d(inputRe, inputIm, n, inverse)
        val outRe = DoubleArray(n * n); val outIm = DoubleArray(n * n)
        val sign = if (inverse) 1.0 else -1.0
        for (v in 0 until n) for (u in 0 until n) {
            var sr=0.0; var si=0.0
            for (y in 0 until n) for (x in 0 until n) {
                val a=sign*2.0*PI*(u*x+v*y)/n; val c=cos(a); val s=sin(a); val i=y*n+x
                sr += inputRe[i]*c-inputIm[i]*s; si += inputRe[i]*s+inputIm[i]*c
            }
            val scale=if(inverse) 1.0/(n*n) else 1.0; outRe[v*n+u]=sr*scale; outIm[v*n+u]=si*scale
        }
        return ComplexGrid(n,outRe,outIm)
    }
    private fun fft2d(inputRe:DoubleArray,inputIm:DoubleArray,n:Int,inverse:Boolean):ComplexGrid {
        val re=inputRe.copyOf();val im=inputIm.copyOf();val ar=DoubleArray(n);val ai=DoubleArray(n)
        for(y in 0 until n){System.arraycopy(re,y*n,ar,0,n);System.arraycopy(im,y*n,ai,0,n);fft1d(ar,ai,inverse);System.arraycopy(ar,0,re,y*n,n);System.arraycopy(ai,0,im,y*n,n)}
        for(x in 0 until n){for(y in 0 until n){ar[y]=re[y*n+x];ai[y]=im[y*n+x]};fft1d(ar,ai,inverse);for(y in 0 until n){re[y*n+x]=ar[y];im[y*n+x]=ai[y]}}
        return ComplexGrid(n,re,im)
    }
    private fun fft1d(re:DoubleArray,im:DoubleArray,inverse:Boolean){val n=re.size;var j=0;for(i in 1 until n){var bit=n shr 1;while(j and bit != 0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val tr=re[i];re[i]=re[j];re[j]=tr;val ti=im[i];im[i]=im[j];im[j]=ti}}
        var len=2;while(len<=n){val a=(if(inverse)2 else -2)*PI/len;val lr=cos(a);val li=sin(a);for(start in 0 until n step len){var wr=1.0;var wi=0.0;for(o in 0 until len/2){val e=start+o;val q=e+len/2;val vr=re[q]*wr-im[q]*wi;val vi=re[q]*wi+im[q]*wr;val ur=re[e];val ui=im[e];re[e]=ur+vr;im[e]=ui+vi;re[q]=ur-vr;im[q]=ui-vi;val nr=wr*lr-wi*li;wi=wr*li+wi*lr;wr=nr}};len=len shl 1};if(inverse)for(i in 0 until n){re[i]/=n;im[i]/=n}}
}

object PsfEngine {
    fun calculate(w: LowOrderWavefront, pupil: PupilModel, wavelengthNm: Double=555.0, size: Int=24): PsfResult {
        require(size >= 8 && wavelengthNm in 380.0..780.0)
        val re=DoubleArray(size*size); val im=DoubleArray(size*size); val lambda=wavelengthNm*1e-9
        for(y in 0 until size) for(x in 0 until size) {
            val nx=(2.0*(x+.5)/size-1.0); val ny=(2.0*(y+.5)/size-1.0); val rho=hypot(nx,ny)
            if(rho<=1.0){val phase=2.0*PI*WavefrontModel.valueMeters(w,rho,atan2(ny,nx))/lambda; val i=y*size+x; re[i]=cos(phase); im[i]=sin(phase)}
        }
        val f=FourierReferenceV2.transform2d(re,im,size,false); val raw=DoubleArray(size*size){f.real[it].pow(2)+f.imag[it].pow(2)}
        val shifted=DoubleArray(raw.size){i->val y=i/size;val x=i%size;raw[((y+size/2)%size)*size+(x+size/2)%size]}
        val sum=shifted.sum(); require(sum>0); for(i in shifted.indices) shifted[i]/=sum
        return PsfResult(size,shifted,w,pupil,wavelengthNm)
    }
}

object OtfEngine {
    fun calculate(psf: PsfResult): OtfResult {
        val n=psf.size
        val unshifted=DoubleArray(n*n){i->val y=i/n;val x=i%n;psf.values[((y+n/2)%n)*n+(x+n/2)%n]}
        val f=FourierReferenceV2.transform2d(unshifted,DoubleArray(n*n),n,false); val dc=hypot(f.real[0],f.imag[0]); require(dc>0)
        return OtfResult(n,DoubleArray(n*n){f.real[it]/dc},DoubleArray(n*n){f.imag[it]/dc})
    }
}

data class PrecompensationParameters(val regularizationK: Double, val maxGain: Double=4.0,
    val rangeMethod: RangeMethod=RangeMethod.RANGE_COMPRESSION) { init { require(regularizationK>0&&maxGain>=1) } }
data class OpticalMetrics(val mseBefore:Double,val mseAfter:Double,val psnrBeforeDb:Double,val psnrAfterDb:Double,
    val clippingFraction:Double,val overshoot:Double,val undershoot:Double,val contrastBefore:Double,val contrastAfter:Double,
    val objective:Double,val processingNanos:Long)
data class PrecompensationResult(val displayed:DoubleArray,val simulatedBefore:DoubleArray,val simulatedAfter:DoubleArray,
    val parameters:PrecompensationParameters,val metrics:OpticalMetrics)

object RetinalSimulationEngine {
    fun apply(image:DoubleArray, otf:OtfResult):DoubleArray { val n=otf.size;require(image.size==n*n)
        val f=FourierReferenceV2.transform2d(image,DoubleArray(image.size),n,false)
        val rr=DoubleArray(image.size);val ii=DoubleArray(image.size)
        for(i in image.indices){rr[i]=f.real[i]*otf.real[i]-f.imag[i]*otf.imag[i];ii[i]=f.real[i]*otf.imag[i]+f.imag[i]*otf.real[i]}
        return FourierReferenceV2.transform2d(rr,ii,n,true).real
    }
}

object PrecompensationEngine {
    fun optimize(target:DoubleArray,otf:OtfResult,kValues:List<Double> = listOf(1e-4,3e-4,1e-3,3e-3,1e-2,3e-2,1e-1,3e-1,1.0)):PrecompensationResult {
        val candidates = kValues.flatMap { k -> listOf(1.25, 2.0, 4.0).flatMap { gain ->
            RangeMethod.entries.map { method -> render(target,otf,PrecompensationParameters(k,gain,method)) }
        } }
        val improving = candidates.filter { it.metrics.mseAfter < it.metrics.mseBefore }
        return (improving.ifEmpty { candidates }).minBy { it.metrics.objective }
    }
    fun render(target:DoubleArray,otf:OtfResult,p:PrecompensationParameters):PrecompensationResult { val start=System.nanoTime();val n=otf.size;require(target.size==n*n)
        val f=FourierReferenceV2.transform2d(target,DoubleArray(target.size),n,false);val rr=DoubleArray(target.size);val ii=DoubleArray(target.size)
        for(i in target.indices){val hr=otf.real[i];val hi=otf.imag[i];val h2=hr*hr+hi*hi;var gr=hr/(h2+p.regularizationK);var gi=-hi/(h2+p.regularizationK);val gain=hypot(gr,gi);if(gain>p.maxGain){gr*=p.maxGain/gain;gi*=p.maxGain/gain};if(i==0){gr=1.0;gi=0.0};rr[i]=f.real[i]*gr-f.imag[i]*gi;ii[i]=f.real[i]*gi+f.imag[i]*gr}
        val raw=FourierReferenceV2.transform2d(rr,ii,n,true).real;val over=max(0.0,(raw.maxOrNull()?:1.0)-1.0);val under=max(0.0,-(raw.minOrNull()?:0.0));val clip=raw.count{it !in 0.0..1.0}.toDouble()/raw.size
        val shown=range(raw,p.rangeMethod);val before=RetinalSimulationEngine.apply(target,otf);val after=RetinalSimulationEngine.apply(shown,otf);val eb=mse(target,before);val ea=mse(target,after);val cb=contrast(before);val ca=contrast(after)
        val objective=ea+0.25*abs(contrast(target)-ca)+0.5*clip+0.1*(over+under)
        return PrecompensationResult(shown,before,after,p,OpticalMetrics(eb,ea,psnr(eb),psnr(ea),clip,over,under,cb,ca,objective,System.nanoTime()-start))
    }
    private fun range(v:DoubleArray,m:RangeMethod):DoubleArray=when(m){RangeMethod.HARD_CLIP->v.map{it.coerceIn(0.0,1.0)}.toDoubleArray();RangeMethod.SOFT_CLIP->v.map{.5+.5*tanh(2.0*(it-.5))}.toDoubleArray();RangeMethod.RANGE_COMPRESSION->{val lo=v.minOrNull()!!;val hi=v.maxOrNull()!!;if(lo>=0&&hi<=1)v.copyOf() else v.map{(it-lo)/(hi-lo).coerceAtLeast(1e-12)}.toDoubleArray()}}
    fun mse(a:DoubleArray,b:DoubleArray)=a.indices.sumOf{(a[it]-b[it]).pow(2)}/a.size
    private fun contrast(a:DoubleArray)=(a.maxOrNull()!!-a.minOrNull()!!)/(a.maxOrNull()!!+a.minOrNull()!!+1e-12)
    private fun psnr(m:Double)=if(m<=1e-15)Double.POSITIVE_INFINITY else 10*log10(1/m)
}

data class EyeOpticalModel(val source:OpticalInputSource,val powerVector:PowerVector,val pupil:PupilModel,
    val wavefront:LowOrderWavefront,val psf:PsfResult,val otf:OtfResult,val approximation:String="LOW_ORDER_ONLY")
object EyeOpticalModelFactory { fun prescription(rx:Spherocylinder,pupil:PupilModel,wavelengthNm:Double=555.0,size:Int=24):EyeOpticalModel { val pv=PrescriptionNormalizer.powerVector(rx);val wf=WavefrontModel.fromPrescription(pv,pupil);val psf=PsfEngine.calculate(wf,pupil,wavelengthNm,size);return EyeOpticalModel(OpticalInputSource.PRESCRIPTION,pv,pupil,wf,psf,OtfEngine.calculate(psf)) } }

object OpticalValidation {
    fun target(size:Int):DoubleArray=DoubleArray(size*size){i->val x=i%size;val y=i/size;if(x in size/4 until 3*size/4 && (y%6)<3) 0.08 else 0.92}
    fun finite(vararg arrays:DoubleArray)=arrays.all{a->a.all{it.isFinite()}}
}
