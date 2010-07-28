/* NSC -- new Scala compiler
 * Copyright 2007-2010 LAMP/EPFL
 * @author  David Bernard, Manohar Jonnalagedda
 */

package scala.tools.nsc
package doc
package html
package page

import model._

import scala.xml.{NodeSeq, Text}
import scala.collection.mutable.HashSet

class Template(tpl: DocTemplateEntity) extends HtmlPage {

  val path =
    templateToPath(tpl)

  val title = tpl.universe.settings.doctitle.value + " (" + tpl.universe.settings.docversion.value + ") — " + tpl.qualifiedName

  val headers =
    <xml:group>
      <link href={ relativeLinkTo(List("template.css", "lib")) }   media="screen" type="text/css" rel="stylesheet"/>
		  <script type="text/javascript" src={ relativeLinkTo{List("jquery.js", "lib")} }></script>
      <script type="text/javascript" src={ relativeLinkTo{List("tools.tooltip.js", "lib")} }></script>
      <script type="text/javascript" src={ relativeLinkTo{List("template.js", "lib")} }></script>
    </xml:group>

  val valueMembers =
    (tpl.methods ::: tpl.values ::: (tpl.templates filter { tpl => tpl.isObject || tpl.isPackage })) sortBy (_.name)

  val typeMembers =
    (tpl.abstractTypes ::: tpl.aliasTypes ::: (tpl.templates filter { tpl => tpl.isTrait || tpl.isClass })) sortBy (_.name)

  val constructors = (tpl match {
    case cls: Class => cls.constructors
    case _ => Nil
  }) sortBy (_.name)

  val body =
    <body class={ if (tpl.isTrait || tpl.isClass) "type" else "value" } onload="windowTitle();">

      { if (tpl.isRootPackage || tpl.inTemplate.isRootPackage)
          NodeSeq.Empty
        else
          <p id="owner">{ templatesToHtml(tpl.inTemplate.toRoot.reverse.tail, xml.Text(".")) }</p>
      }

      <div id="definition">
        <img src={ relativeLinkTo(List(kindToString(tpl) + "_big.png", "lib")) }/>
        <h1>{ if (tpl.isRootPackage) "root package" else tpl.name }</h1>
      </div>

      { signature(tpl, true) }
      { memberToCommentHtml(tpl, true) }

      <div id="template">

        <div id="mbrsel">
          <div id='textfilter'><span class='pre'/><input type='text' accesskey='/'/><span class='post'/></div>
          { if (tpl.linearization.isEmpty) NodeSeq.Empty else
              <div id="order">
                <span class="filtertype">Ordering</span>
                <ol><li class="alpha in">Alphabetic</li><li class="inherit out">By inheritance</li></ol>
              </div>
          }
          { if (tpl.linearization.isEmpty) NodeSeq.Empty else
              <div id="ancestors">
                <span class="filtertype">Inherited</span>
                <ol><li class="hideall">Hide All</li><li class="showall">Show all</li></ol>
                <ol id="linearization">{ (tpl :: tpl.linearizationTemplates) map { wte => <li class="in" name={ wte.qualifiedName }>{ wte.name }</li> } }</ol>
              </div>
          }
          {
            <div id="visbl">
              <span class="filtertype">Visibility</span>
              <ol><li class="public in">Public</li><li class="all out">All</li></ol>
            </div>
          }
          {
            <div id="impl">
              <span class="filtertype">Impl.</span>
              <ol><li class="concrete in">Concrete</li><li class="abstract in">Abstract</li></ol>
            </div>
          }
        </div>

        { if (constructors.isEmpty) NodeSeq.Empty else
            <div id="constructors" class="members">
              <h3>Instance constructors</h3>
              <ol>{ constructors map (memberToHtml(_)) }</ol>
            </div>
        }

        { if (typeMembers.isEmpty) NodeSeq.Empty else
            <div id="types" class="types members">
              <h3>Type Members</h3>
              <ol>{ typeMembers map (memberToHtml(_)) }</ol>
            </div>
        }

        { if (valueMembers.isEmpty) NodeSeq.Empty else
            <div id="values" class="values members">
              <h3>Value Members</h3>
              <ol>{ valueMembers map (memberToHtml(_)) }</ol>
            </div>
        }

        {
          NodeSeq fromSeq (for ((superTpl, superType) <- tpl.linearization) yield
            <div class="parent" name={ superTpl.qualifiedName }>
              <h3>Inherited from {
                if (tpl.universe.settings.useStupidTypes.value)
                  superTpl match {
                    case dtpl: DocTemplateEntity =>
                      val sig = signature(dtpl, false, true) \ "_"
                      sig
                    case tpl: TemplateEntity =>
                      tpl.name
                  }
                else
                  typeToHtml(superType, true)
              }</h3>
            </div>
          )
        }

      </div>

      <div id="tooltip" ></div>

    </body>

  def boundsToString(hi: Option[TypeEntity], lo: Option[TypeEntity]): String = {
    def bound0(bnd: Option[TypeEntity], pre: String): String = bnd match {
      case None => ""
      case Some(tpe) => pre ++ tpe.toString
    }
    bound0(hi, "<:") ++ bound0(lo, ">:")
  }

  def tparamsToString(tpss: List[TypeParam]): String =
    if (tpss.isEmpty) "" else {
      def tparam0(tp: TypeParam): String =
         tp.variance + tp.name + boundsToString(tp.hi, tp.lo)
      def tparams0(tpss: List[TypeParam]): String = (tpss: @unchecked) match {
        case tp :: Nil => tparam0(tp)
        case tp :: tps => tparam0(tp) ++ ", " ++ tparams0(tps)
      }
      "[" + tparams0(tpss) + "]"
    }

  def defParamsToString(d: MemberEntity with Def):String = {
    val namess = for( ps <- d.valueParams ) yield
      for( p <- ps ) yield p.resultType.name
    tparamsToString(d.typeParams) + namess.foldLeft("") { (s,names) => s + (names mkString("(",",",")")) }
  }

  def memberToHtml(mbr: MemberEntity): NodeSeq = {
    val defParamsString = mbr match {
      case d:MemberEntity with Def => defParamsToString(d)
      case _ => ""
    }
    <li name={ mbr.definitionName } visbl={ if (mbr.visibility.isProtected) "prt" else "pub" }
      data-isabs={ mbr.isAbstract.toString }>
      <a id={ mbr.name +defParamsString +":"+ mbr.resultType.name}/>
      { signature(mbr, false) }
      { memberToCommentHtml(mbr, false) }
    </li>
  }

  def memberToCommentHtml(mbr: MemberEntity, isSelf: Boolean): NodeSeq = {
    val useCaseCommentHtml = mbr match {
      case nte: NonTemplateMemberEntity if nte.isUseCase =>
        inlineToHtml(comment.Text("[use case] "))
      case _ => NodeSeq.Empty
    }
    mbr match {
      case dte: DocTemplateEntity if isSelf =>
        // comment of class itself
        <div id="comment" class="fullcomment">{ memberToCommentBodyHtml(mbr, isSelf = true) }</div>
      case dte: DocTemplateEntity if mbr.comment.isDefined =>
        // comment of inner, documented class (only short comment, full comment is on the class' own page)
        <p class="comment cmt">{ inlineToHtml(mbr.comment.get.short) }</p>
      case _ =>
        // comment of non-class member or non-documentented inner class
        val commentBody = memberToCommentBodyHtml(mbr, isSelf = false)
        if (commentBody.isEmpty)
          NodeSeq.Empty
        else {
          <xml:group>
            { if (mbr.comment.isEmpty) NodeSeq.Empty else {
                <p class="shortcomment cmt">{ useCaseCommentHtml }{ inlineToHtml(mbr.comment.get.short) }</p>
              }
            }
            <div class="fullcomment">{ useCaseCommentHtml }{ memberToCommentBodyHtml(mbr, isSelf) }</div>
          </xml:group>
        }
    }
  }

  def memberToCommentBodyHtml(mbr: MemberEntity, isSelf: Boolean): NodeSeq =
    NodeSeq.Empty ++
    { if (mbr.comment.isEmpty) NodeSeq.Empty else
        <div class="comment cmt">{ commentToHtml(mbr.comment) }</div>
    } ++
    { val prs: List[ParameterEntity] = mbr match {
        case cls: Class if cls.isCaseClass =>
          cls.typeParams ::: (cls.primaryConstructor map (_.valueParams.flatten)).toList.flatten
        case trt: Trait => trt.typeParams
        case dfe: Def => dfe.typeParams ::: dfe.valueParams.flatten
        case ctr: Constructor => ctr.valueParams.flatten
        case _ => Nil
      }
      def mbrCmt = mbr.comment.get
      def paramCommentToHtml(prs: List[ParameterEntity]): NodeSeq = prs match {
        case Nil =>
          NodeSeq.Empty
        case (tp: TypeParam) :: rest =>
          val paramEntry: NodeSeq = {
            <dt class="tparam">{ tp.name }</dt><dd class="cmt">{ bodyToHtml(mbrCmt.typeParams(tp.name)) }</dd>
          }
          paramEntry ++ paramCommentToHtml(rest)
        case (vp: ValueParam) :: rest  =>
          val paramEntry: NodeSeq = {
            <dt class="param">{ vp.name }</dt><dd class="cmt">{ bodyToHtml(mbrCmt.valueParams(vp.name)) }</dd>
          }
          paramEntry ++ paramCommentToHtml(rest)
      }
      if (mbr.comment.isEmpty) NodeSeq.Empty
      else {
        val cmtedPrs = prs filter {
          case tp: TypeParam => mbrCmt.typeParams isDefinedAt tp.name
          case vp: ValueParam => mbrCmt.valueParams isDefinedAt vp.name
        }
        if (cmtedPrs.isEmpty && mbrCmt.result.isEmpty) NodeSeq.Empty
        else
          <dl class="paramcmts block">{
            paramCommentToHtml(cmtedPrs) ++ (
            mbrCmt.result match {
              case None => NodeSeq.Empty
              case Some(cmt) =>
                <dt>returns</dt><dd class="cmt">{ bodyToHtml(cmt) }</dd>
            })
          }</dl>
      }
    } ++
    { val fvs: List[comment.Paragraph] = visibility(mbr).toList ::: mbr.flags
      if (fvs.isEmpty) NodeSeq.Empty else
        <div class="block">
          attributes: { fvs map { fv => { inlineToHtml(fv.text) ++ xml.Text(" ") } } }
        </div>
    } ++
    { tpl.companion match {
        case Some(companion) if isSelf =>
          <div class="block">
            go to: <a href={relativeLinkTo(companion)}>companion</a>
          </div>
        case _ =>
          NodeSeq.Empty
      }
    } ++
    { val inDefTpls = mbr.inDefinitionTemplates
      if (inDefTpls.tail.isEmpty && (inDefTpls.head == mbr.inTemplate)) NodeSeq.Empty else {
        <div class="block">
          definition classes: { templatesToHtml(inDefTpls, xml.Text(" → ")) }
        </div>
      }
    } ++
    { mbr match {
        case dtpl: DocTemplateEntity if (isSelf && !dtpl.linearization.isEmpty) =>
          <div class="block">
            linear super types: { typesToHtml(dtpl.linearizationTypes, hasLinks = true, sep = xml.Text(", ")) }
          </div>
        case _ => NodeSeq.Empty
      }
    } ++
    { mbr match {
        case dtpl: DocTemplateEntity if (isSelf && !dtpl.subClasses.isEmpty) =>
          <div class="block">
            known subclasses: { templatesToHtml(dtpl.subClasses, xml.Text(", ")) }
          </div>
        case _ => NodeSeq.Empty
      }
    } ++
    { mbr match {
        case dtpl: DocTemplateEntity if (isSelf && !dtpl.selfType.isEmpty) =>
          <div class="block">
            self type: { typeToHtml(dtpl.selfType.get, hasLinks = true) }
          </div>
        case _ => NodeSeq.Empty
      }
    } ++
    { mbr match {
        case dtpl: DocTemplateEntity if (isSelf && dtpl.sourceUrl.isDefined && dtpl.inSource.isDefined) =>
          val (absFile, line) = dtpl.inSource.get
          <div class="block">
            source: { <a href={ dtpl.sourceUrl.get.toString }>{ Text(absFile.file.getName) }</a> }
          </div>
        case _ => NodeSeq.Empty
      }
    } ++
    { if (mbr.deprecation.isEmpty) NodeSeq.Empty else
        <div class="block"><ol>deprecated:
          { <li>{ bodyToHtml(mbr.deprecation.get) }</li> }
        </ol></div>
    } ++
    { mbr.comment match {
        case Some(comment) =>
          <xml:group>
            { if(!comment.version.isEmpty)
                <div class="block"><ol>version
                  { for(body <- comment.version.toList) yield <li>{bodyToHtml(body)}</li> }
                </ol></div>
              else NodeSeq.Empty
            }
            { if(!comment.since.isEmpty)
                <div class="block"><ol>since
                  { for(body <- comment.since.toList) yield <li>{bodyToHtml(body)}</li> }
                </ol></div>
              else NodeSeq.Empty
            }
            { if(!comment.see.isEmpty)
                <div class="block"><ol>see also:
                  { val seeXml:List[scala.xml.NodeSeq]=(for(see <- comment.see ) yield <li>{bodyToHtml(see)}</li> )
                    seeXml.reduceLeft(_ ++ Text(", ") ++ _)
                  }
                </ol></div>
              else NodeSeq.Empty
            }
          </xml:group>
        case None => NodeSeq.Empty
      }
    }

  def kindToString(mbr: MemberEntity): String = mbr match {
    case tpl: DocTemplateEntity => docEntityKindToString(tpl)
    case ctor: Constructor => "new"
    case tme: MemberEntity =>
      ( if (tme.isImplicit) "implicit " else "" ) +
      ( if (tme.isDef) "def"
        else if (tme.isVal) "val"
        else if (tme.isLazyVal) "lazy val"
        else if (tme.isVar) "var"
        else "type")
  }

  def boundsToHtml(hi: Option[TypeEntity], lo: Option[TypeEntity], hasLinks: Boolean): NodeSeq = {
    def bound0(bnd: Option[TypeEntity], pre: String): NodeSeq = bnd match {
      case None => NodeSeq.Empty
      case Some(tpe) => xml.Text(pre) ++ typeToHtml(tpe, hasLinks)
    }
    bound0(lo, " >: ") ++ bound0(hi, " <: ")
  }

  def visibility(mbr: MemberEntity): Option[comment.Paragraph] = {
    import comment._
    import comment.{ Text => CText }
    mbr.visibility match {
      case PrivateInInstance() =>
        Some(Paragraph(CText("private[this]")))
      case PrivateInTemplate(owner) if (owner == mbr.inTemplate) =>
        Some(Paragraph(CText("private")))
      case PrivateInTemplate(owner) =>
        Some(Paragraph(Chain(List(CText("private["), EntityLink(owner), CText("]")))))
      case ProtectedInInstance() =>
        Some(Paragraph(CText("protected[this]")))
      case ProtectedInTemplate(owner) if (owner == mbr.inTemplate) =>
        Some(Paragraph(CText("protected")))
      case ProtectedInTemplate(owner) =>
        Some(Paragraph(Chain(List(CText("protected["), EntityLink(owner), CText("]")))))
      case Public() =>
        None
    }
  }

  /** name, tparams, params, result */
  def signature(mbr: MemberEntity, isSelf: Boolean, isReduced: Boolean = false): NodeSeq = {
    def inside(hasLinks: Boolean): NodeSeq =
      <xml:group>
      <span class="kind">{ kindToString(mbr) }</span>
      <span class="symbol">
        <span class={"name" + (if (mbr.deprecation.isDefined) " deprecated" else "") }>{ if (mbr.isConstructor) tpl.name else mbr.name }</span>
        {
          def tparamsToHtml(mbr: Entity): NodeSeq = mbr match {
            case hk: HigherKinded =>
              val tpss = hk.typeParams
              if (tpss.isEmpty) NodeSeq.Empty else {
                def tparam0(tp: TypeParam): NodeSeq =
                  <span name={ tp.name }>{ tp.variance + tp.name }{ tparamsToHtml(tp) }{ boundsToHtml(tp.hi, tp.lo, hasLinks)}</span>
                def tparams0(tpss: List[TypeParam]): NodeSeq = (tpss: @unchecked) match {
                  case tp :: Nil => tparam0(tp)
                  case tp :: tps => tparam0(tp) ++ Text(", ") ++ tparams0(tps)
                }
                <span class="tparams">[{ tparams0(tpss) }]</span>
              }
              case _ => NodeSeq.Empty
          }
          tparamsToHtml(mbr)
        }
        { if (isReduced) NodeSeq.Empty else {
          def paramsToHtml(vlsss: List[List[ValueParam]]): NodeSeq = {
            def param0(vl: ValueParam): NodeSeq =
              // notice the }{ in the next lines, they are necessary to avoid a undesired withspace in output
              <span name={ vl.name }>{ Text(vl.name + ": ") }{ typeToHtml(vl.resultType, hasLinks) }{
                if(!vl.defaultValue.isEmpty) {
                  Text(" = ") ++ <span class="default">{vl.defaultValue.get}</span>
                }
                else NodeSeq.Empty
              }</span>
            def params0(vlss: List[ValueParam]): NodeSeq = vlss match {
              case Nil => NodeSeq.Empty
              case vl :: Nil => param0(vl)
              case vl :: vls => param0(vl) ++ Text(", ") ++ params0(vls)
            }
            def implicitCheck(vlss: List[ValueParam]): NodeSeq = vlss match {
              case vl :: vls => if(vl.isImplicit) { <span class="implicit">implicit </span> } else Text("")
              case _ => Text("")
            }
            vlsss map { vlss => <span class="params">({implicitCheck(vlss) ++ params0(vlss) })</span> }
          }
          mbr match {
            case cls: Class if cls.isCaseClass && cls.primaryConstructor.isDefined =>
              paramsToHtml(cls.primaryConstructor.get.valueParams)
            case ctr: Constructor => paramsToHtml(ctr.valueParams)
            case dfe: Def => paramsToHtml(dfe.valueParams)
            case _ => NodeSeq.Empty
          }
        }}
        { if (isReduced) NodeSeq.Empty else {
          mbr match {
            case tpl: DocTemplateEntity if (!tpl.isPackage) =>
              tpl.parentType match {
                case Some(st) => <span class="result"> extends { typeToHtml(st, hasLinks) }</span>
                case None =>NodeSeq.Empty
              }
            case tme: MemberEntity if (tme.isDef || tme.isVal || tme.isLazyVal || tme.isVar) =>
              <span class="result">: { typeToHtml(tme.resultType, hasLinks) }</span>
            case abt: AbstractType =>
              val b2s = boundsToHtml(abt.hi, abt.lo, hasLinks)
              if (b2s != NodeSeq.Empty)
                <span class="result">{ b2s }</span>
              else NodeSeq.Empty
            case alt: AliasType =>
              <span class="result"> = { typeToHtml(alt.alias, hasLinks) }</span>
            case _ => NodeSeq.Empty
          }
        }}
      </span>
      </xml:group>
    mbr match {
      case dte: DocTemplateEntity if !isSelf =>
        <h4 class="signature"><a href={ relativeLinkTo(dte) }>{ inside(hasLinks = false) }</a></h4>
      case _ if isSelf =>
        <h4 id="signature" class="signature">{ inside(hasLinks = true) }</h4>
      case _ =>
        <h4 class="signature">{ inside(hasLinks = true) }</h4>
    }
  }

}
